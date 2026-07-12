package com.gagastudio.finmate.auth;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOnboardingStatusService {
	private final FinmateUserRepository users;

	UserOnboardingStatusService(FinmateUserRepository users) {
		this.users = users;
	}

	@Transactional
	public void complete(UUID userId, String displayName) {
		FinmateUser user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
		user.completeGoalOnboarding(displayName.trim());
	}
}
