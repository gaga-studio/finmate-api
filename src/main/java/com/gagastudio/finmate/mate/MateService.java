package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.goals.GoalAccessService;
import com.gagastudio.finmate.goals.GoalDtosBridge;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MateService {
	private static final Instant FIXTURE_SYNCED_AT = Instant.parse("2026-07-13T00:00:00Z");
	private final MateGroupRepository groups;
	private final RecommendedAdventurerRepository adventurers;
	private final SyntheticPublicProfileRepository publicProfiles;
	private final AdventurerRoutineRepository routines;
	private final RoutineAdaptationRepository adaptations;
	private final RoutineBuildRepository builds;
	private final RoutineCandidateGenerator candidates;
	private final RoutineIdempotencyStore idempotencyCommands;
	private final RoutineCommandLock commandLock;
	private final ObjectMapper objectMapper;
	private final GoalAccessService goalAccess;

	MateService(MateGroupRepository groups, RecommendedAdventurerRepository adventurers,
		SyntheticPublicProfileRepository publicProfiles, AdventurerRoutineRepository routines,
		RoutineAdaptationRepository adaptations, RoutineBuildRepository builds, RoutineCandidateGenerator candidates,
		RoutineIdempotencyStore idempotencyCommands, RoutineCommandLock commandLock, ObjectMapper objectMapper,
		GoalAccessService goalAccess) {
		this.groups = groups;
		this.adventurers = adventurers;
		this.publicProfiles = publicProfiles;
		this.routines = routines;
		this.adaptations = adaptations;
		this.builds = builds;
		this.candidates = candidates;
		this.idempotencyCommands = idempotencyCommands;
		this.commandLock = commandLock;
		this.objectMapper = objectMapper;
		this.goalAccess = goalAccess;
	}

	MateDtos.MateGroupPage groups() {
		return new MateDtos.MateGroupPage(groups.findAllByOrderById().stream().map(this::groupView).toList());
	}

	MateDtos.AdventurerPage adventurers(String groupId) {
		requireGroup(groupId);
		List<RecommendedAdventurer> candidates = adventurers.findByGroupIdOrderById(groupId);
		Set<UUID> activeProfileIds = Set.copyOf(publicProfiles.findByIdInAndConsentState(
			candidates.stream().map(RecommendedAdventurer::getPublicProfileId).toList(), "ACTIVE").stream()
			.map(SyntheticPublicProfile::getId).toList());
		return new MateDtos.AdventurerPage(groupId, candidates.stream()
			.filter(candidate -> activeProfileIds.contains(candidate.getPublicProfileId()))
			.map(this::adventurerView).toList(),
			"mate-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	MateDtos.MateGroupReportView groupReport(String groupId) {
		MateGroup group = groups.findById(groupId).orElseThrow(MateNotFoundException::new);
		List<MateDtos.AdventurerView> preview = adventurers(groupId).items().stream().limit(3).toList();
		return new MateDtos.MateGroupReportView(groupView(group),
			List.of("비슷한 여윳돈 구간", "주거비 부담 중간", "여행자금 목표"),
			new MateDtos.DistributionRange(4_300, 5_100, 5_900),
			new MateDtos.DistributionRange(1_600, 2_300, 3_100),
			new GoalDtosBridge.FinancialStats(5_600, 2_300, 4_200, 55), 8, preview,
			List.of("GROUP_SAVING_STABLE_V1", "GROUP_SPENDING_RANGE_V1"),
			"group-report-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	MateDtos.AdventurerView adventurer(String groupId, String adventurerId) {
		return adventurerView(adventurerEntity(groupId, adventurerId));
	}

	MateDtos.AdventurerReportView adventurerReport(UUID userId, String groupId, String adventurerId) {
		MateDtos.AdventurerView adventurer = adventurer(groupId, adventurerId);
		GoalAccessService.RoutineGoalContext context = goalAccess.routineContext(userId);
		return new MateDtos.AdventurerReportView(adventurer, List.of(
			new MateDtos.ComparisonMetric("저축률 구간", percent(context.savingRateBps()), "20~25%", "SAVING_GAP_ACHIEVABLE_V1"),
			new MateDtos.ComparisonMetric("루틴 유지기간", "활성 루틴 없음", "3개월 이상", "ROUTINE_DURATION_GAP_V1")),
			List.of("월급 입금 직후 정기 저축", "검증 기간 동안 연속 유지"),
			"adventurer-report-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	MateDtos.AdventurerPage search(MateDtos.MateExploreSearchRequest request) {
		boolean supportedCombination = "AGE_24_29".equals(request.ageBand())
			&& "EARLY_CAREER".equals(request.occupationGroup())
			&& "FROM_200_TO_300".equals(request.incomeBand())
			&& "BALANCED".equals(request.spendingTendency())
			&& "FROM_10_TO_20".equals(request.savingRateBand())
			&& "BALANCED".equals(request.investmentTendency());
		if (supportedCombination) return adventurers("group-saving-30");
		return new MateDtos.AdventurerPage("group-saving-30", List.of(), "mate-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	MateDtos.RoutineView routine(String groupId, String adventurerId, String routineId) {
		AdventurerRoutine routine = routineEntity(groupId, adventurerId, routineId);
		return routineView(routine);
	}

	@Transactional
	Object createRecommendation(UUID userId, MateDtos.CreateAdaptationRequest request) {
		if (request.isLegacy()) return createAdaptation(userId, request);
		AdventurerRoutine routine = routineEntity(request.groupId(), request.adventurerId(), request.resolvedRoutineId());
		goalAccess.requireActiveGoal(userId);
		if (!routine.domains().contains(request.selectedDomain())) throw new InvalidAdaptationDomainException();
		RoutineAdaptation adaptation = adaptations.save(new RoutineAdaptation(userId, routine, Instant.now()));
		adaptation.selectDomain(request.selectedDomain(), Instant.now());
		GoalAccessService.RoutineGoalContext context = goalAccess.routineContext(userId);
		List<RoutineCandidate> generated = candidates.generate(request.selectedDomain(), context.standardMonthlyAmountKrw());
		int durationDays = durationDays(context);
		List<MateDtos.CandidateView> options = generated.stream()
			.map(candidate -> candidateView(candidate, durationDays)).toList();
		MateDtos.CandidateView recommended = options.stream()
			.filter(candidate -> "STANDARD".equals(candidate.difficulty())).findFirst().orElseThrow();
		return new MateDtos.RoutineRecommendationView(adaptation.getId().toString(), routine.getId(),
			request.selectedDomain(), recommended, "PAYDAY_SAVE_STANDARD_FROM_BASELINE_V1",
			"SAVING".equals(request.selectedDomain()) ? "hana-saving-info-001" : null,
			options, "adapt-calc-v2", "FRESH", FIXTURE_SYNCED_AT);
	}

	@Transactional
	MateDtos.AdaptationAwaitingView createAdaptation(UUID userId, MateDtos.CreateAdaptationRequest request) {
		AdventurerRoutine routine = routineEntity(request.groupId(), request.adventurerId(), request.resolvedRoutineId());
		goalAccess.requireActiveGoal(userId);
		RoutineAdaptation adaptation = adaptations.save(new RoutineAdaptation(userId, routine, Instant.now()));
		return awaitingView(adaptation, routine.domains());
	}

	MateDtos.RelatedHanaProductInfoView relatedProduct(UUID userId, String productId) {
		goalAccess.requireActiveGoal(userId);
		if (!"hana-saving-info-001".equals(productId)) throw new MateNotFoundException();
		return new MateDtos.RelatedHanaProductInfoView(productId, "검수된 하나 저축상품 정보 예시", "적립식 저축", "SAVING",
			List.of("가입 대상과 납입 조건은 공식 상품설명서에서 확인", "금리와 우대조건은 기준일에 따라 달라질 수 있음"),
			List.of("이 카드는 가입 권유가 아닌 정보 제공용", "상품 열람은 XP와 목표 진행에 영향을 주지 않음"),
			"2026-07-13", "https://www.hanabank.com/", true, false, false);
	}

	@Transactional
	MateDtos.AdaptationSetView chooseDomain(UUID userId, UUID adaptationId, MateDtos.ChooseDomainRequest request) {
		goalAccess.requireActiveGoal(userId);
		RoutineAdaptation adaptation = adaptation(userId, adaptationId);
		if (!"AWAITING_DOMAIN".equals(adaptation.getState())) throw new InvalidAdaptationDomainException();
		AdventurerRoutine routine = routines.findById(adaptation.getSourceRoutineId()).orElseThrow(MateNotFoundException::new);
		if (!routine.domains().contains(request.domain())) throw new InvalidAdaptationDomainException();
		adaptation.selectDomain(request.domain(), Instant.now());
		GoalAccessService.RoutineGoalContext context = goalAccess.routineContext(userId);
		return adaptationSet(adaptation, candidates.generate(request.domain(), context.standardMonthlyAmountKrw()),
			durationDays(context));
	}

	@Transactional
	RoutineCommandResult<MateDtos.ActiveBuildView> importCandidate(UUID userId, UUID adaptationId, String candidateId, String idempotencyKey) {
		goalAccess.requireActiveGoal(userId);
		validateIdempotencyKey(idempotencyKey);
		commandLock.lock(userId);
		String fingerprint = RoutineRequestFingerprint.importCandidate(adaptationId, candidateId);
		Optional<RoutineCommandResult<MateDtos.ActiveBuildView>> replay = replay(userId, "IMPORT", idempotencyKey,
			fingerprint, MateDtos.ActiveBuildView.class);
		if (replay.isPresent()) return replay.get();
		if (builds.findByUserIdAndStatus(userId, "ACTIVE").isPresent()) throw new ActiveRoutineBuildException();
		RoutineAdaptation adaptation = readyAdaptation(userId, adaptationId);
		RoutineCandidate candidate = candidate(userId, adaptation, candidateId);
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
		goalAccess.requireActiveGoal(userId);
		validateIdempotencyKey(idempotencyKey);
		commandLock.lock(userId);
		UUID adaptationId = adaptationId(request.adaptationId());
		String fingerprint = RoutineRequestFingerprint.replacement(adaptationId, request.candidateId(), request.confirmReplacement());
		Optional<RoutineCommandResult<MateDtos.ReplacementView>> replay = replay(userId, "REPLACE", idempotencyKey,
			fingerprint, MateDtos.ReplacementView.class);
		if (replay.isPresent()) return replay.get();
		RoutineBuild active = builds.findByUserIdAndStatus(userId, "ACTIVE").orElseThrow(MateNotFoundException::new);
		RoutineAdaptation adaptation = readyAdaptation(userId, adaptationId);
		RoutineCandidate candidate = candidate(userId, adaptation, request.candidateId());
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

	private RoutineCandidate candidate(UUID userId, RoutineAdaptation adaptation, String candidateId) {
		return generatedCandidates(userId, adaptation.getSelectedDomain()).stream()
			.filter(candidate -> candidate.candidateId().equals(candidateId)).findFirst().orElseThrow(MateNotFoundException::new);
	}

	private List<RoutineCandidate> generatedCandidates(UUID userId, String domain) {
		return candidates.generate(domain, goalAccess.routineContext(userId).standardMonthlyAmountKrw());
	}

	private RoutineAdaptation adaptation(UUID userId, UUID adaptationId) {
		return adaptations.findByIdAndUserId(adaptationId, userId).orElseThrow(MateNotFoundException::new);
	}

	private AdventurerRoutine routineEntity(String groupId, String adventurerId, String routineId) {
		requireGroup(groupId);
		RecommendedAdventurer adventurer = adventurerEntity(groupId, adventurerId);
		publicProfiles.findById(adventurer.getPublicProfileId())
			.filter(profile -> "ACTIVE".equals(profile.getConsentState()))
			.orElseThrow(MateNotFoundException::new);
		return routines.findByIdAndGroupIdAndAdventurerId(routineId, groupId, adventurerId).orElseThrow(MateNotFoundException::new);
	}

	private RecommendedAdventurer adventurerEntity(String groupId, String adventurerId) {
		requireGroup(groupId);
		RecommendedAdventurer adventurer = adventurers.findById(adventurerId)
			.filter(candidate -> candidate.getGroupId().equals(groupId))
			.orElseThrow(MateNotFoundException::new);
		publicProfiles.findById(adventurer.getPublicProfileId())
			.filter(profile -> "ACTIVE".equals(profile.getConsentState()))
			.orElseThrow(MateNotFoundException::new);
		return adventurer;
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
			.map(routine -> new MateDtos.RoutineSummary(routine.getId(), routine.getTitle(), primaryDomain(routine), routine.getMaintainedDays())).toList();
		return new MateDtos.AdventurerView(adventurer.getId(), adventurer.getGroupId(), adventurer.getAlias(),
			List.of("사회초년생", "자취"), adventurer.reasons(), "여행자금 목표 달성", summaries,
			adventurer.getApprovedAt().minusSeconds(3_600), adventurer.getApprovedAt());
	}

	private MateDtos.RoutineView routineView(AdventurerRoutine routine) {
		return new MateDtos.RoutineView(routine.getId(), routine.getAdventurerId(), routine.getGroupId(), routine.getTitle(),
			primaryDomain(routine), routine.getMaintainedDays(), List.of("월급 입금일 확인", "입금 당일 자동저축 확인"),
			List.of("PAYDAY_TRANSFER_VERIFIED_V1", "ROUTINE_MAINTENANCE_VERIFIED_V1"));
	}

	private MateDtos.AdaptationAwaitingView awaitingView(RoutineAdaptation adaptation, List<String> domains) {
		return new MateDtos.AdaptationAwaitingView(adaptation.getId().toString(), adaptation.getSourceRoutineId(), adaptation.getState(), domains,
			"adapt-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	private MateDtos.AdaptationSetView adaptationSet(RoutineAdaptation adaptation, List<RoutineCandidate> generated,
		int durationDays) {
		return new MateDtos.AdaptationSetView(adaptation.getId().toString(), adaptation.getSourceRoutineId(), adaptation.getState(), adaptation.getSelectedDomain(),
			candidateView(generated.get(0), durationDays), candidateView(generated.get(1), durationDays),
			candidateView(generated.get(2), durationDays), "adapt-calc-v1", "FRESH", FIXTURE_SYNCED_AT);
	}

	private MateDtos.CandidateView candidateView(RoutineCandidate candidate, int durationDays) {
		return new MateDtos.CandidateView(candidate.candidateId(), candidate.difficulty(), candidate.domain(), candidate.title(), candidate.targetKind(),
			candidate.targetAmountKrw(), candidate.targetRatioBps(), candidate.behaviorTarget(), durationDays, candidate.steps());
	}

	private int durationDays(GoalAccessService.RoutineGoalContext context) {
		return Math.max(30, context.remainingMonths() * 30);
	}

	private MateDtos.ActiveBuildView buildView(RoutineBuild build) {
		return new MateDtos.ActiveBuildView(build.getId().toString(), build.getCandidateId(), build.getSourceRoutineId(), build.getDomain(),
			build.getDifficulty(), build.getStatus(), build.steps(), build.getActivatedAt(), build.getArchivedAt(), id(build.getReplacesBuildId()),
			id(build.getReplacedByBuildId()), "build-calc-v1", "FRESH", build.getLastSyncedAt());
	}

	private String id(UUID id) {
		return id == null ? null : id.toString();
	}

	private String primaryDomain(AdventurerRoutine routine) {
		if (routine.getId().contains("save")) return "SAVING";
		return routine.domains().get(0);
	}

	private String percent(int basisPoints) {
		return basisPoints % 100 == 0 ? (basisPoints / 100) + "%" : (basisPoints / 100.0) + "%";
	}
}
