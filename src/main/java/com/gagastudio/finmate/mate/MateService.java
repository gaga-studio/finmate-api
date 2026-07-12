package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MateService {
	private static final Instant FIXTURE_SYNCED_AT = Instant.parse("2026-07-13T00:00:00Z");
	private final MateGroupRepository groups;
	private final RecommendedAdventurerRepository adventurers;
	private final AdventurerRoutineRepository routines;
	private final RoutineAdaptationRepository adaptations;
	private final RoutineBuildRepository builds;
	private final RoutineCandidateGenerator candidates;
	private final RoutineIdempotencyStore idempotencyCommands;
	private final RoutineCommandLock commandLock;
	private final ObjectMapper objectMapper;

	MateService(MateGroupRepository groups, RecommendedAdventurerRepository adventurers, AdventurerRoutineRepository routines,
		RoutineAdaptationRepository adaptations, RoutineBuildRepository builds, RoutineCandidateGenerator candidates,
		RoutineIdempotencyStore idempotencyCommands, RoutineCommandLock commandLock, ObjectMapper objectMapper) {
		this.groups = groups;
		this.adventurers = adventurers;
		this.routines = routines;
		this.adaptations = adaptations;
		this.builds = builds;
		this.candidates = candidates;
		this.idempotencyCommands = idempotencyCommands;
		this.commandLock = commandLock;
		this.objectMapper = objectMapper;
	}

	MateDtos.MateGroupPage groups() {
		return new MateDtos.MateGroupPage(groups.findAllByOrderById().stream().map(this::groupView).toList());
	}

	MateDtos.AdventurerPage adventurers(String groupId) {
		requireGroup(groupId);
		return new MateDtos.AdventurerPage(groupId, adventurers.findByGroupIdOrderById(groupId).stream().map(this::adventurerView).toList(),
			"mate-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	MateDtos.RoutineView routine(String groupId, String adventurerId, String routineId) {
		AdventurerRoutine routine = routineEntity(groupId, adventurerId, routineId);
		return routineView(routine);
	}

	@Transactional
	MateDtos.AdaptationAwaitingView createAdaptation(UUID userId, MateDtos.CreateAdaptationRequest request) {
		AdventurerRoutine routine = routineEntity(request.groupId(), request.adventurerId(), request.routineId());
		RoutineAdaptation adaptation = adaptations.save(new RoutineAdaptation(userId, routine, Instant.now()));
		return awaitingView(adaptation, routine.domains());
	}

	@Transactional
	MateDtos.AdaptationSetView chooseDomain(UUID userId, UUID adaptationId, MateDtos.ChooseDomainRequest request) {
		RoutineAdaptation adaptation = adaptation(userId, adaptationId);
		if (!"AWAITING_DOMAIN".equals(adaptation.getState())) throw new InvalidAdaptationDomainException();
		AdventurerRoutine routine = routines.findById(adaptation.getSourceRoutineId()).orElseThrow(MateNotFoundException::new);
		if (!routine.domains().contains(request.domain())) throw new InvalidAdaptationDomainException();
		adaptation.selectDomain(request.domain(), Instant.now());
		return adaptationSet(adaptation, candidates.generate(request.domain()));
	}

	@Transactional
	RoutineCommandResult<MateDtos.ActiveBuildView> importCandidate(UUID userId, UUID adaptationId, String candidateId, String idempotencyKey) {
		validateIdempotencyKey(idempotencyKey);
		commandLock.lock(userId);
		String fingerprint = RoutineRequestFingerprint.importCandidate(adaptationId, candidateId);
		Optional<RoutineCommandResult<MateDtos.ActiveBuildView>> replay = replay(userId, "IMPORT", idempotencyKey,
			fingerprint, MateDtos.ActiveBuildView.class);
		if (replay.isPresent()) return replay.get();
		if (builds.findByUserIdAndStatus(userId, "ACTIVE").isPresent()) throw new ActiveRoutineBuildException();
		RoutineAdaptation adaptation = readyAdaptation(userId, adaptationId);
		RoutineCandidate candidate = candidate(adaptation, candidateId);
		Instant now = Instant.now();
		RoutineBuild build = saveActiveBuild(new RoutineBuild(userId, adaptation, candidate, now, null, "IMPORT", idempotencyKey));
		MateDtos.ActiveBuildView body = buildView(build);
		StoredRoutineCommand command = new StoredRoutineCommand(userId, "IMPORT", idempotencyKey, fingerprint, 201,
			writeBody(body), build.getId(), null, null, now);
		if (!idempotencyCommands.insert(command)) {
			return replay(userId, "IMPORT", idempotencyKey, fingerprint, MateDtos.ActiveBuildView.class)
				.orElseThrow(ActiveRoutineBuildException::new);
		}
		return new RoutineCommandResult<>(201, body);
	}

	MateDtos.ActiveBuildView activeBuild(UUID userId) {
		return buildView(builds.findByUserIdAndStatus(userId, "ACTIVE").orElseThrow(MateNotFoundException::new));
	}

	public Object activeBuildForHome(UUID userId) {
		return builds.findByUserIdAndStatus(userId, "ACTIVE").map(this::buildView).orElse(null);
	}

	@Transactional
	RoutineCommandResult<MateDtos.ReplacementView> replaceActiveBuild(UUID userId, String idempotencyKey,
		MateDtos.ReplaceBuildRequest request) {
		validateIdempotencyKey(idempotencyKey);
		commandLock.lock(userId);
		UUID adaptationId = adaptationId(request.adaptationId());
		String fingerprint = RoutineRequestFingerprint.replacement(adaptationId, request.candidateId(), request.confirmReplacement());
		Optional<RoutineCommandResult<MateDtos.ReplacementView>> replay = replay(userId, "REPLACE", idempotencyKey,
			fingerprint, MateDtos.ReplacementView.class);
		if (replay.isPresent()) return replay.get();
		RoutineBuild active = builds.findByUserIdAndStatus(userId, "ACTIVE").orElseThrow(MateNotFoundException::new);
		RoutineAdaptation adaptation = readyAdaptation(userId, adaptationId);
		RoutineCandidate candidate = candidate(adaptation, request.candidateId());
		Instant now = Instant.now();
		active.archive(now);
		builds.flush();
		RoutineBuild replacement = saveActiveBuild(new RoutineBuild(userId, adaptation, candidate, now, active.getId(), "REPLACE", idempotencyKey));
		active.linkReplacement(replacement.getId(), now);
		MateDtos.ReplacementView body = new MateDtos.ReplacementView(buildView(active), buildView(replacement), now);
		StoredRoutineCommand command = new StoredRoutineCommand(userId, "REPLACE", idempotencyKey, fingerprint, 200,
			writeBody(body), null, active.getId(), replacement.getId(), now);
		if (!idempotencyCommands.insert(command)) {
			return replay(userId, "REPLACE", idempotencyKey, fingerprint, MateDtos.ReplacementView.class)
				.orElseThrow(ActiveRoutineBuildException::new);
		}
		return new RoutineCommandResult<>(200, body);
	}

	private RoutineAdaptation readyAdaptation(UUID userId, UUID adaptationId) {
		RoutineAdaptation adaptation = adaptation(userId, adaptationId);
		if (!"CANDIDATES_READY".equals(adaptation.getState())) throw new InvalidAdaptationDomainException();
		return adaptation;
	}

	private RoutineCandidate candidate(RoutineAdaptation adaptation, String candidateId) {
		return candidates.generate(adaptation.getSelectedDomain()).stream()
			.filter(candidate -> candidate.candidateId().equals(candidateId)).findFirst().orElseThrow(MateNotFoundException::new);
	}

	private RoutineAdaptation adaptation(UUID userId, UUID adaptationId) {
		return adaptations.findByIdAndUserId(adaptationId, userId).orElseThrow(MateNotFoundException::new);
	}

	private AdventurerRoutine routineEntity(String groupId, String adventurerId, String routineId) {
		requireGroup(groupId);
		adventurers.findById(adventurerId).filter(adventurer -> adventurer.getGroupId().equals(groupId)).orElseThrow(MateNotFoundException::new);
		return routines.findByIdAndGroupIdAndAdventurerId(routineId, groupId, adventurerId).orElseThrow(MateNotFoundException::new);
	}

	private void requireGroup(String groupId) {
		groups.findById(groupId).orElseThrow(MateNotFoundException::new);
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
			throw new InvalidRoutineBuildRequestException("Idempotency-Key must contain 16 to 128 characters");
		}
	}

	private UUID adaptationId(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			throw new InvalidRoutineBuildRequestException("adaptationId must be a UUID");
		}
	}

	private RoutineBuild saveActiveBuild(RoutineBuild build) {
		try {
			return builds.saveAndFlush(build);
		} catch (DataIntegrityViolationException exception) {
			if (isUniqueViolation(exception)) throw new ActiveRoutineBuildException();
			throw exception;
		}
	}

	private boolean isUniqueViolation(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) return true;
		}
		return false;
	}

	private <T> Optional<RoutineCommandResult<T>> replay(UUID userId, String operation, String idempotencyKey,
		String fingerprint, Class<T> bodyType) {
		return idempotencyCommands.find(userId, operation, idempotencyKey).map(command -> {
			if (!command.requestFingerprint().equals(fingerprint)) throw new IdempotencyKeyConflictException();
			return new RoutineCommandResult<>(command.originalStatus(), readBody(command.originalBody(), bodyType));
		});
	}

	private String writeBody(Object body) {
		try {
			return objectMapper.writeValueAsString(body);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Routine command response could not be serialized", exception);
		}
	}

	private <T> T readBody(String body, Class<T> bodyType) {
		try {
			return objectMapper.readValue(body, bodyType);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Stored routine command response could not be read", exception);
		}
	}

	private MateDtos.MateGroupView groupView(MateGroup group) {
		return new MateDtos.MateGroupView(group.getId(), group.getName(), group.getMemberCount(), group.isSyntheticDemo(),
			group.isEligibleForProductionAggregation());
	}

	private MateDtos.AdventurerView adventurerView(RecommendedAdventurer adventurer) {
		List<MateDtos.RoutineSummary> summaries = routines.findByGroupIdAndAdventurerIdOrderById(adventurer.getGroupId(), adventurer.getId()).stream()
			.map(routine -> new MateDtos.RoutineSummary(routine.getId(), routine.getTitle(), routine.domains())).toList();
		return new MateDtos.AdventurerView(adventurer.getId(), adventurer.getGroupId(), adventurer.getAlias(), adventurer.reasons(), summaries,
			adventurer.getApprovedAt());
	}

	private MateDtos.RoutineView routineView(AdventurerRoutine routine) {
		return new MateDtos.RoutineView(routine.getId(), routine.getAdventurerId(), routine.getGroupId(), routine.getTitle(),
			routine.getDescription(), routine.domains(), routine.getMaintainedDays());
	}

	private MateDtos.AdaptationAwaitingView awaitingView(RoutineAdaptation adaptation, List<String> domains) {
		return new MateDtos.AdaptationAwaitingView(adaptation.getId().toString(), adaptation.getSourceRoutineId(), adaptation.getState(), domains,
			"adapt-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	private MateDtos.AdaptationSetView adaptationSet(RoutineAdaptation adaptation, List<RoutineCandidate> generated) {
		return new MateDtos.AdaptationSetView(adaptation.getId().toString(), adaptation.getSourceRoutineId(), adaptation.getState(), adaptation.getSelectedDomain(),
			candidateView(generated.get(0)), candidateView(generated.get(1)), candidateView(generated.get(2)), "adapt-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	private MateDtos.CandidateView candidateView(RoutineCandidate candidate) {
		return new MateDtos.CandidateView(candidate.candidateId(), candidate.difficulty(), candidate.domain(), candidate.title(), candidate.targetKind(),
			candidate.targetAmountKrw(), candidate.targetRatioBps(), candidate.behaviorTarget(), candidate.steps());
	}

	private MateDtos.ActiveBuildView buildView(RoutineBuild build) {
		return new MateDtos.ActiveBuildView(build.getId().toString(), build.getCandidateId(), build.getSourceRoutineId(), build.getDomain(),
			build.getDifficulty(), build.getStatus(), build.steps(), build.getActivatedAt(), build.getArchivedAt(), id(build.getReplacesBuildId()),
			id(build.getReplacedByBuildId()), "build-calc-v1", "FRESH", build.getLastSyncedAt());
	}

	private String id(UUID id) {
		return id == null ? null : id.toString();
	}
}
