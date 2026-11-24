package com.grantora.repository;
import com.grantora.entity.GrantUtilizationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GrantUtilizationRepository extends JpaRepository<GrantUtilizationRecord,Long> {
    List<GrantUtilizationRecord> findByApplication_Beneficiary_User_IdOrderBySubmittedAtDesc(Long userId);
    List<GrantUtilizationRecord> findAllByOrderBySubmittedAtDesc();
}
