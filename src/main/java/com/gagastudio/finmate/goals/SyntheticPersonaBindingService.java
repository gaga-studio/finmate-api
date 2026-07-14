package com.gagastudio.finmate.goals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
class SyntheticPersonaBindingService {
	static final String RELEASE_VERSION = "v1.0.0";
	private final SyntheticPersonaBindingRepository bindings;
	private final SyntheticRuntimePersonaRepository personas;
	private final ObjectMapper objectMapper;

	SyntheticPersonaBindingService(SyntheticPersonaBindingRepository bindings, SyntheticRuntimePersonaRepository personas,
		ObjectMapper objectMapper) {
		this.bindings = bindings;
		this.personas = personas;
		this.objectMapper = objectMapper;
	}

	SyntheticPersonaBindingView bindIfEligible(UUID userId, OnboardingState state) {
		SyntheticPersonaBinding existing = bindings.findByUserId(userId).orElse(null);
		if (existing != null) return bound(existing);
		List<SyntheticRuntimePersona> candidates = personas.findBindingCandidates(RELEASE_VERSION,
			state.getIncomeRegularity(), state.getHousingType());
		if (candidates.isEmpty()) return SyntheticPersonaBindingView.insufficient();
		candidates.sort(Comparator.comparingInt((SyntheticRuntimePersona persona) -> preferenceScore(persona, state)).reversed()
			.thenComparing(persona -> persona.getAgeBand()).thenComparing(persona -> persona.getOccupationGroup()));
		int highestScore = preferenceScore(candidates.getFirst(), state);
		List<SyntheticRuntimePersona> preferred = candidates.stream()
			.filter(persona -> preferenceScore(persona, state) == highestScore).toList();
		SyntheticRuntimePersona chosen = preferred.get(Math.floorMod((userId + RELEASE_VERSION).hashCode(), preferred.size()));
		try {
			SyntheticPersonaBinding binding = bindings.saveAndFlush(new SyntheticPersonaBinding(userId,
				chosen.getSourcePersonaId(), RELEASE_VERSION, Instant.now()));
			return bound(binding);
		} catch (DataIntegrityViolationException exception) {
			return bindings.findByUserId(userId).map(this::bound).orElseThrow(() -> exception);
		}
	}

	SyntheticPersonaBindingView bindingFor(UUID userId) {
		return bindings.findByUserId(userId).map(this::bound).orElseGet(SyntheticPersonaBindingView::insufficient);
	}

	List<SyntheticRuntimePersona> discoverablePeers(UUID userId) {
		SyntheticPersonaBindingView binding = bindingFor(userId);
		return "BOUND".equals(binding.status())
			? personas.findDiscoverableExcludingSourcePersonaId(binding.releaseVersion(), binding.sourcePersonaId())
			: List.of();
	}

	private SyntheticPersonaBindingView bound(SyntheticPersonaBinding binding) {
		return new SyntheticPersonaBindingView("BOUND", binding.getSourcePersonaId(), binding.getReleaseVersion());
	}

	private int preferenceScore(SyntheticRuntimePersona persona, OnboardingState state) {
		int score = 0;
		if (!"UNKNOWN".equals(state.getOccupationGroup()) && state.getOccupationGroup().equals(persona.getOccupationGroup())) score += 8;
		if (!"UNKNOWN".equals(state.getAgeBand()) && state.getAgeBand().equals(persona.getAgeBand())) score += 4;
		if (state.getMoneyConcern().equals(persona.getMoneyWorry())) score += 2;
		if (sharesLifestyleTag(state.lifestyleTags(), persona.getLifestyleTags())) score++;
		return score;
	}

	private boolean sharesLifestyleTag(List<String> userTags, String personaTags) {
		try {
			List<String> tags = objectMapper.readValue(personaTags, new TypeReference<>() { });
			return userTags.stream().anyMatch(tags::contains);
		} catch (Exception exception) {
			throw new IllegalStateException("Stored synthetic persona lifestyle tags are invalid", exception);
		}
	}

}
