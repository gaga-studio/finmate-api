package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MateService {
	private static final Instant FIXTURE_SYNCED_AT = Instant.parse("2026-07-13T00:00:00Z");
	private final MateGroupRepository groups;
	private final RecommendedAdventurerRepository adventurers;
	private final AdventurerRoutineRepository routines;
	private final RoutineAdaptationRepository adaptations;
	private final RoutineBuildRepository builds;
	private final RoutineCandidateGenerator candidates;

	MateService(MateGroupRepository groups, RecommendedAdventurerRepository adventurers, AdventurerRoutineRepository routines,
		RoutineAdaptationRepository adaptations, RoutineBuildRepository builds, RoutineCandidateGenerator candidates) {
		this.groups = groups;
		this.adventurers = adventurers;
		this.routines = routines;
		this.adaptations = adaptations;
		this.builds = builds;
		this.candidates = candidates;
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
	MateDtos.ActiveBuildView importCandidate(UUID userId, UUID adaptationId, String candidateId, String idempotencyKey) {
		validateIdempotencyKey(idempotencyKey);
		RoutineBuild replay = builds.findByUserIdAndCommandTypeAndIdempotencyKey(userId, "IMPORT", idempotencyKey).orElse(null);
		if (replay != null) return buildView(replay);
		if (builds.findByUserIdAndStatus(userId, "ACTIVE").isPresent()) throw new ActiveRoutineBuildException();
		RoutineAdaptation adaptation = readyAdaptation(userId, adaptationId);
		RoutineCandidate candidate = candidate(adaptation, candidateId);
		return buildView(builds.save(new RoutineBuild(userId, adaptation, candidate, Instant.now(), null, "IMPORT", idempotencyKey)));
	}

	MateDtos.ActiveBuildView activeBuild(UUID userId) {
		return buildView(builds.findByUserIdAndStatus(userId, "ACTIVE").orElseThrow(MateNotFoundException::new));
	}

	@Transactional
	MateDtos.ReplacementView replaceActiveBuild(UUID userId, String idempotencyKey, MateDtos.ReplaceBuildRequest request) {
		validateIdempotencyKey(idempotencyKey);
		RoutineBuild replay = builds.findByUserIdAndCommandTypeAndIdempotencyKey(userId, "REPLACE", idempotencyKey).orElse(null);
		if (replay != null) return replacementView(userId, replay, replay.getActivatedAt());
		RoutineBuild active = builds.findByUserIdAndStatus(userId, "ACTIVE").orElseThrow(MateNotFoundException::new);
		RoutineAdaptation adaptation = readyAdaptation(userId, UUID.fromString(request.adaptationId()));
		RoutineCandidate candidate = candidate(adaptation, request.candidateId());
		Instant now = Instant.now();
		active.archive(now);
		builds.flush();
		RoutineBuild replacement = builds.saveAndFlush(new RoutineBuild(userId, adaptation, candidate, now, active.getId(), "REPLACE", idempotencyKey));
		active.linkReplacement(replacement.getId(), now);
		return new MateDtos.ReplacementView(buildView(active), buildView(replacement), now);
	}

	private MateDtos.ReplacementView replacementView(UUID userId, RoutineBuild replacement, Instant replacedAt) {
		RoutineBuild archived = builds.findByIdAndUserId(replacement.getReplacesBuildId(), userId).orElseThrow(MateNotFoundException::new);
		return new MateDtos.ReplacementView(buildView(archived), buildView(replacement), replacedAt);
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
