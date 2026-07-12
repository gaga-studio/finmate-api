package com.gagastudio.finmate.goals;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OnboardingStateRepository extends JpaRepository<OnboardingState, UUID> {
}
