package com.gagastudio.finmate.mate;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicProfileDisclosureProjection {
	private final SyntheticPublicProfileRepository profiles;

	PublicProfileDisclosureProjection(SyntheticPublicProfileRepository profiles) {
		this.profiles = profiles;
	}

	public void activate(UUID ownerUserId, String visibleFields, String consentVersion, Instant updatedAt) {
		profiles.findByOwnerUserId(ownerUserId).ifPresent(profile -> {
			profile.activateDisclosure(visibleFields, consentVersion, updatedAt);
			profiles.save(profile);
		});
	}

	public void withdraw(UUID ownerUserId, Instant updatedAt) {
		profiles.findByOwnerUserId(ownerUserId).ifPresent(profile -> {
			profile.withdrawDisclosure(updatedAt);
			profiles.save(profile);
		});
	}
}
