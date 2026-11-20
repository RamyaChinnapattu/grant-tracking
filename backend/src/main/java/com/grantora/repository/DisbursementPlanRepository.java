package com.grantora.repository;

import com.grantora.entity.DisbursementPlan;
import com.grantora.entity.DisbursementPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisbursementPlanRepository extends JpaRepository<DisbursementPlan, Long> {

    Optional<DisbursementPlan> findByApplication_Id(Long applicationId);

    boolean existsByApplication_Id(Long applicationId);

    boolean existsByApplication_IdAndPlanStatusNot(Long applicationId, DisbursementPlanStatus status);

    Page<DisbursementPlan> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<DisbursementPlan> findAllByOrderByCreatedAtDesc();

    List<DisbursementPlan> findByPlanStatus(DisbursementPlanStatus planStatus);

    @Query("SELECT p FROM DisbursementPlan p JOIN FETCH p.application a JOIN FETCH a.beneficiary b WHERE p.id = :id")
    Optional<DisbursementPlan> findDetailedById(@Param("id") Long id);
}
