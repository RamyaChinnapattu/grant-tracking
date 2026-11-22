package com.grantora.repository;

import com.grantora.entity.DisbursementInstallment;
import com.grantora.entity.InstallmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DisbursementInstallmentRepository extends JpaRepository<DisbursementInstallment, Long> {

    List<DisbursementInstallment> findByPlan_IdOrderByInstallmentNumberAsc(Long planId);

    List<DisbursementInstallment> findByStatus(InstallmentStatus status);

    boolean existsByPaymentReference(String paymentReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM DisbursementInstallment i WHERE i.id = :id")
    Optional<DisbursementInstallment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT i FROM DisbursementInstallment i WHERE i.status = :status AND i.plannedReleaseDate <= :targetDate")
    List<DisbursementInstallment> findDueInstallments(
        @Param("status") InstallmentStatus status,
        @Param("targetDate") LocalDate targetDate
    );

    @Query("SELECT i.status, count(i), sum(i.amount) FROM DisbursementInstallment i GROUP BY i.status")
    List<Object[]> getStatusSummary();
}
