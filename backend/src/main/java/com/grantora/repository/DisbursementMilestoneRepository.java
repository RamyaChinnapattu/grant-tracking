package com.grantora.repository;

import com.grantora.entity.DisbursementMilestone;
import com.grantora.entity.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DisbursementMilestoneRepository extends JpaRepository<DisbursementMilestone, Long> {

    List<DisbursementMilestone> findByInstallment_IdOrderByDueDateAsc(Long installmentId);

    List<DisbursementMilestone> findByInstallment_Plan_Id(Long planId);

    List<DisbursementMilestone> findByStatus(MilestoneStatus status);

    List<DisbursementMilestone> findByNonCompliantTrue();

    @Query("SELECT m FROM DisbursementMilestone m WHERE m.status NOT IN ('COMPLETED', 'CANCELLED') AND m.dueDate BETWEEN :startDate AND :endDate")
    List<DisbursementMilestone> findUpcomingMilestones(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT m FROM DisbursementMilestone m WHERE m.status NOT IN ('COMPLETED', 'CANCELLED') AND m.dueDate < :currentDate")
    List<DisbursementMilestone> findOverdueMilestones(@Param("currentDate") LocalDate currentDate);

    @Query("SELECT m FROM DisbursementMilestone m JOIN FETCH m.installment i JOIN FETCH i.plan p JOIN FETCH p.application a ORDER BY m.dueDate ASC")
    List<DisbursementMilestone> findAllDetailed();
}
