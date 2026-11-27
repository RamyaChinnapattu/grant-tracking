package com.grantora.repository;

import com.grantora.entity.BeneficiaryEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryEmailLogRepository extends JpaRepository<BeneficiaryEmailLog, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<BeneficiaryEmailLog> findByIdempotencyKey(String idempotencyKey);

    List<BeneficiaryEmailLog> findByBeneficiaryIdOrderByCreatedAtDesc(Long beneficiaryId);

    List<BeneficiaryEmailLog> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
