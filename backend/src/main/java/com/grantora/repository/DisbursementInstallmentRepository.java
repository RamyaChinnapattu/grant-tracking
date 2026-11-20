package com.grantora.repository;

import com.grantora.entity.DisbursementInstallment;
import com.grantora.entity.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DisbursementInstallmentRepository extends JpaRepository<DisbursementInstallment, Long> {

    List<DisbursementInstallment> findByPlan_IdOrderByInstallmentNumberAsc(Long planId);

    List<DisbursementInstallment> findByStatus(InstallmentStatus status);

    @Query("SELECT i FROM DisbursementInstallment i WHERE i.status = :status AND i.plannedReleaseDate <= :targetDate")
    List<DisbursementInstallment> findDueInstallments(
        @Param("status") InstallmentStatus status,
        @Param("targetDate") LocalDate targetDate
    );

    @Query("SELECT i.status, count(i), sum(i.amount) FROM DisbursementInstallment i GROUP BY i.status")
    List<Object[]> getStatusSummary();
}
