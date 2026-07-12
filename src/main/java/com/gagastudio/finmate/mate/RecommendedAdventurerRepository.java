package com.gagastudio.finmate.mate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecommendedAdventurerRepository extends JpaRepository<RecommendedAdventurer, String> {
	List<RecommendedAdventurer> findByGroupIdOrderById(String groupId);
}
