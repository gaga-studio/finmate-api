package com.gagastudio.finmate.records;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DailyReflectionRepository extends JpaRepository<DailyReflection, DailyReflection.DailyReflectionId> {
	Optional<DailyReflection> findByIdUserIdAndIdRecordDate(UUID userId, LocalDate date);
}
