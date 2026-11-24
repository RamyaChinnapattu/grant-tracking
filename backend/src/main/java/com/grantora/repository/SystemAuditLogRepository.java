package com.grantora.repository;

import com.grantora.entity.SystemAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, Long> {
    List<SystemAuditLog> findAllByOrderByTimestampDesc();
}
