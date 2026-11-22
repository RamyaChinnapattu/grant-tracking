package com.grantora.repository;
import com.grantora.entity.ComplianceReminderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ComplianceReminderHistoryRepository extends JpaRepository<ComplianceReminderHistory, Long> {
    boolean existsByCompliance_IdAndEventTypeAndEventDate(Long complianceId, String eventType, java.time.LocalDate eventDate);
}
