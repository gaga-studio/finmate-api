package com.gagastudio.finmate.goals;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RaidProjectionAuditRepository extends JpaRepository<RaidProjectionAudit, UUID> {
}
