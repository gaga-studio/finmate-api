package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdventurerRoutineRepository extends JpaRepository<AdventurerRoutine, String> {
	List<AdventurerRoutine> findByGroupIdAndAdventurerIdOrderById(String groupId, String adventurerId);
	Optional<AdventurerRoutine> findByIdAndGroupIdAndAdventurerId(String id, String groupId, String adventurerId);
}
