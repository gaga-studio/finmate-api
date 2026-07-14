package com.gagastudio.finmate.mate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.goals.GoalAccessService;
import com.gagastudio.finmate.goals.GoalDtosBridge;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MateService {
	private static final Instant FIXTURE_SYNCED_AT = Instant.parse("2026-07-13T00:00:00Z");
	private static final String RUNTIME_GROUP_ID = "synthetic-runtime";
	private static final String SEARCH_CALCULATION_VERSION = "mate-search-runtime-v1";
	private static final int SEARCH_LIMIT = 6;
	private static final List<String> RELAXATION_ORDER = List.of(
		"ageBand", "occupationGroup", "spendingTendency", "investmentTendency");
	private static final Map<String, Set<String>> AGE_COMPATIBILITY = Map.of(
		"AGE_19_23", Set.of("AGE_24_29"),
		"AGE_24_29", Set.of("AGE_19_23", "AGE_30_34"),
		"AGE_30_34", Set.of("AGE_24_29"));
	private static final Map<String, Set<String>> OCCUPATION_COMPATIBILITY = Map.of(
		"STUDENT", Set.of("JOB_SEEKER"),
		"JOB_SEEKER", Set.of("STUDENT", "EARLY_CAREER"),
		"EARLY_CAREER", Set.of("JOB_SEEKER", "FREELANCER"),
		"FREELANCER", Set.of("EARLY_CAREER"));
	private static final Map<String, Set<String>> SPENDING_COMPATIBILITY = Map.of(
		"PLANNED", Set.of("BALANCED"),
		"BALANCED", Set.of("PLANNED", "VARIABLE"),
		"VARIABLE", Set.of("BALANCED"));
	private static final Map<String, Set<String>> INVESTMENT_COMPATIBILITY = Map.of(
		"CAUTIOUS", Set.of("BALANCED"),
		"BALANCED", Set.of("CAUTIOUS", "LEARNING"),
		"LEARNING", Set.of("BALANCED"));
	private final MateGroupRepository groups;
	private final RecommendedAdventurerRepository adventurers;
	private final RuntimeMateCandidateRepository runtimeCandidates;
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
		RuntimeMateCandidateRepository runtimeCandidates,
		SyntheticPublicProfileRepository publicProfiles, AdventurerRoutineRepository routines,
		RoutineAdaptationRepository adaptations, RoutineBuildRepository builds, RoutineCandidateGenerator candidates,
		RoutineIdempotencyStore idempotencyCommands, RoutineCommandLock commandLock, ObjectMapper objectMapper,
		GoalAccessService goalAccess) {
		this.groups = groups;
		this.adventurers = adventurers;
		this.runtimeCandidates = runtimeCandidates;
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
		if (RUNTIME_GROUP_ID.equals(groupId)) return runtimeAdventurer(adventurerId);
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

	MateDtos.MateExploreSearchPage search(UUID userId, MateDtos.MateExploreSearchRequest request) {
		List<RuntimeMateCandidate> eligible = runtimeCandidates.findEligible(userId,
			request.incomeBand(), request.savingRateBand());
		if (eligible.isEmpty()) {
			return new MateDtos.MateExploreSearchPage(List.of(), 0, "NONE", List.of(),
				SEARCH_CALCULATION_VERSION, "FRESH", null);
		}

		int targetCount = Math.min(SEARCH_LIMIT, eligible.size());
		int relaxedCount = 0;
		List<RuntimeMateCandidate> matching = matchingCandidates(eligible, request, relaxedCount);
		while (matching.size() < targetCount && relaxedCount < RELAXATION_ORDER.size()) {
			relaxedCount++;
			matching = matchingCandidates(eligible, request, relaxedCount);
		}

		List<RuntimeMateCandidate> selected = matching.stream()
			.sorted(Comparator
				.<RuntimeMateCandidate>comparingInt(candidate -> similarityScore(candidate, request)).reversed()
				.thenComparing(Comparator.comparingInt(this::maintenanceDays).reversed())
				.thenComparing(RuntimeMateCandidate::adventurerId))
			.limit(SEARCH_LIMIT)
			.toList();
		if (selected.isEmpty()) {
			return new MateDtos.MateExploreSearchPage(List.of(), eligible.size(), "NONE",
				List.copyOf(RELAXATION_ORDER.subList(0, relaxedCount)), SEARCH_CALCULATION_VERSION,
				"FRESH", null);
		}
		boolean relaxed = selected.stream().anyMatch(candidate -> similarityScore(candidate, request) < 10_000);
		List<String> relaxedFilters = relaxed
			? List.copyOf(RELAXATION_ORDER.subList(0, relaxedCount))
			: List.of();
		Instant lastSyncedAt = selected.stream().map(RuntimeMateCandidate::lastSyncedAt)
			.max(Comparator.naturalOrder()).orElse(null);
		return new MateDtos.MateExploreSearchPage(
			selected.stream().map(candidate -> searchCard(candidate, request)).toList(),
			eligible.size(), relaxed ? "RELAXED" : "EXACT", relaxedFilters,
			SEARCH_CALCULATION_VERSION, "FRESH", lastSyncedAt);
	}

	MateDtos.RoutineView routine(String groupId, String adventurerId, String routineId) {
		if (RUNTIME_GROUP_ID.equals(groupId)) return runtimeRoutine(adventurerId, routineId);
		AdventurerRoutine routine = routineEntity(groupId, adventurerId, routineId);
		return routineView(routine);
	}

	@Transactional
	Object createRecommendation(UUID userId, MateDtos.CreateAdaptationRequest request) {
		if (request.isLegacy()) return createAdaptation(userId, request);
		if (RUNTIME_GROUP_ID.equals(request.groupId())) return createRuntimeRecommendation(userId, request);
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

	private MateDtos.RoutineRecommendationView createRuntimeRecommendation(UUID userId,
		MateDtos.CreateAdaptationRequest request) {
		RuntimeMateCandidate source = runtimeCandidate(request.adventurerId());
		if (!source.routineId().equals(request.resolvedRoutineId())
			|| !source.routineDomain().equals(request.selectedDomain())) {
			throw new InvalidAdaptationDomainException();
		}
		goalAccess.requireActiveGoal(userId);
		RoutineAdaptation adaptation = adaptations.save(new RoutineAdaptation(userId, RUNTIME_GROUP_ID,
			source.adventurerId(), source.routineId(), source.routineDomain(), Instant.now()));
		GoalAccessService.RoutineGoalContext context = goalAccess.routineContext(userId);
		List<MateDtos.CandidateView> options = candidates.generate(source.routineDomain(),
			context.standardMonthlyAmountKrw()).stream()
			.map(candidate -> candidateView(candidate, durationDays(context)))
			.toList();
		MateDtos.CandidateView recommended = options.stream()
			.filter(candidate -> "STANDARD".equals(candidate.difficulty()))
			.findFirst().orElseThrow();
		return new MateDtos.RoutineRecommendationView(adaptation.getId().toString(), source.routineId(),
			source.routineDomain(), recommended, "RUNTIME_ROUTINE_FROM_BASELINE_V1",
			"SAVING".equals(source.routineDomain()) ? "hana-saving-info-001" : null,
			options, "adapt-calc-v2", "FRESH", source.lastSyncedAt());
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

	private List<RuntimeMateCandidate> matchingCandidates(List<RuntimeMateCandidate> eligible,
		MateDtos.MateExploreSearchRequest request, int relaxedCount) {
		return eligible.stream()
			.filter(candidate -> matches(candidate.ageBand(), request.ageBand(), relaxedCount >= 1,
				AGE_COMPATIBILITY))
			.filter(candidate -> matches(candidate.occupationGroup(), request.occupationGroup(), relaxedCount >= 2,
				OCCUPATION_COMPATIBILITY))
			.filter(candidate -> matches(candidate.spendingTendency(), request.spendingTendency(), relaxedCount >= 3,
				SPENDING_COMPATIBILITY))
			.filter(candidate -> matches(candidate.investmentTendency(), request.investmentTendency(), relaxedCount >= 4,
				INVESTMENT_COMPATIBILITY))
			.toList();
	}

	private boolean matches(String candidate, String requested, boolean relaxed,
		Map<String, Set<String>> compatibility) {
		return candidate.equals(requested)
			|| relaxed && compatibility.getOrDefault(requested, Set.of()).contains(candidate);
	}

	private MateDtos.MateExploreSearchCard searchCard(RuntimeMateCandidate candidate,
		MateDtos.MateExploreSearchRequest request) {
		return new MateDtos.MateExploreSearchCard(
			candidate.adventurerId(), RUNTIME_GROUP_ID, candidate.sourceGroupId(), runtimeAlias(candidate),
			contextTags(candidate), new MateDtos.MateExploreRoutineSummary(
				candidate.routineId(), runtimeRoutineTitle(candidate), candidate.routineDomain()),
			maintenanceDays(candidate), similarityScore(candidate, request), matchedFilters(candidate, request),
			candidate.dataAsOf());
	}

	private int similarityScore(RuntimeMateCandidate candidate, MateDtos.MateExploreSearchRequest request) {
		int score = 0;
		if (candidate.incomeBand().equals(request.incomeBand())) score += 2_500;
		if (candidate.savingRateBand().equals(request.savingRateBand())) score += 2_000;
		if (candidate.occupationGroup().equals(request.occupationGroup())) score += 1_500;
		if (candidate.ageBand().equals(request.ageBand())) score += 1_500;
		if (candidate.spendingTendency().equals(request.spendingTendency())) score += 1_500;
		if (candidate.investmentTendency().equals(request.investmentTendency())) score += 1_000;
		return score;
	}

	private List<String> matchedFilters(RuntimeMateCandidate candidate, MateDtos.MateExploreSearchRequest request) {
		List<String> matches = new ArrayList<>(6);
		if (candidate.ageBand().equals(request.ageBand())) matches.add("ageBand");
		if (candidate.occupationGroup().equals(request.occupationGroup())) matches.add("occupationGroup");
		if (candidate.incomeBand().equals(request.incomeBand())) matches.add("incomeBand");
		if (candidate.spendingTendency().equals(request.spendingTendency())) matches.add("spendingTendency");
		if (candidate.savingRateBand().equals(request.savingRateBand())) matches.add("savingRateBand");
		if (candidate.investmentTendency().equals(request.investmentTendency())) matches.add("investmentTendency");
		return List.copyOf(matches);
	}

	private MateDtos.AdventurerView runtimeAdventurer(String adventurerId) {
		RuntimeMateCandidate candidate = runtimeCandidate(adventurerId);
		return new MateDtos.AdventurerView(candidate.adventurerId(), RUNTIME_GROUP_ID, runtimeAlias(candidate),
			contextTags(candidate), List.of("공개·품질·루틴 기준 충족"),
			"승인된 루틴 %d일 유지".formatted(maintenanceDays(candidate)),
			List.of(runtimeRoutineSummary(candidate)), candidate.lastSyncedAt(), candidate.lastSyncedAt());
	}

	private MateDtos.RoutineView runtimeRoutine(String adventurerId, String routineId) {
		RuntimeMateCandidate candidate = runtimeCandidate(adventurerId);
		if (!candidate.routineId().equals(routineId)) throw new MateNotFoundException();
		String frequency = candidate.routineFrequency() == null || candidate.routineFrequency().isBlank()
			? "승인된 반복 루틴"
			: candidate.routineFrequency();
		return new MateDtos.RoutineView(candidate.routineId(), candidate.adventurerId(), RUNTIME_GROUP_ID,
			runtimeRoutineTitle(candidate), candidate.routineDomain(), maintenanceDays(candidate),
			List.of(frequency), List.of("RUNTIME_ROUTINE_APPROVED_V1", "ROUTINE_MAINTENANCE_VERIFIED_V1"));
	}

	private RuntimeMateCandidate runtimeCandidate(String adventurerId) {
		return runtimeCandidates.findDiscoverableById(adventurerId).orElseThrow(MateNotFoundException::new);
	}

	private MateDtos.RoutineSummary runtimeRoutineSummary(RuntimeMateCandidate candidate) {
		return new MateDtos.RoutineSummary(candidate.routineId(), runtimeRoutineTitle(candidate),
			candidate.routineDomain(), maintenanceDays(candidate));
	}

	private int maintenanceDays(RuntimeMateCandidate candidate) {
		return (int) Math.min(Integer.MAX_VALUE, candidate.maintainedMonths() * 30L);
	}

	private String runtimeAlias(RuntimeMateCandidate candidate) {
		String suffix = Integer.toUnsignedString(candidate.adventurerId().hashCode(), 36).toUpperCase(Locale.ROOT);
		return "익명 모험가 " + suffix;
	}

	private String runtimeRoutineTitle(RuntimeMateCandidate candidate) {
		String normalized = candidate.routineId().strip().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (Set.of("자동저축", "automatic_saving").contains(normalized)) return "자동저축";
		return "카페 방문 줄이기";
	}

	private List<String> contextTags(RuntimeMateCandidate candidate) {
		LinkedHashSet<String> tags = new LinkedHashSet<>();
		tags.add(candidate.ageBand());
		tags.add(candidate.occupationGroup());
		tags.add(candidate.householdType());
		try {
			List<String> lifestyleTags = objectMapper.readValue(candidate.lifestyleTags(), new TypeReference<>() {});
			lifestyleTags.stream().filter(tag -> tag != null && !tag.isBlank()).forEach(tags::add);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Runtime persona lifestyle tags could not be read", exception);
		}
		return List.copyOf(tags);
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
