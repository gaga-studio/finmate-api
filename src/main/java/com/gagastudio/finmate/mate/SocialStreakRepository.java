package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SocialStreakRepository extends JpaRepository<SocialStreak, UUID> {
	List<SocialStreak> findAllByOrderByDisplayOrder();
}
