package com.gagastudio.finmate.records;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecordEventRepository extends JpaRepository<RecordEvent, UUID> {
	List<RecordEvent> findByUserIdAndRecordDateBetweenOrderByOccurredAtAsc(UUID userId, LocalDate from, LocalDate to);
	List<RecordEvent> findByUserIdAndRecordDateOrderByOccurredAtAsc(UUID userId, LocalDate date);
}
