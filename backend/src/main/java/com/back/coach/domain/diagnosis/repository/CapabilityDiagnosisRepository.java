package com.back.coach.domain.diagnosis.repository;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CapabilityDiagnosisRepository extends JpaRepository<CapabilityDiagnosis, Long> {

    Optional<CapabilityDiagnosis> findTopByUserIdOrderByVersionDescCreatedAtDesc(Long userId);

    Optional<CapabilityDiagnosis> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("select max(c.version) from CapabilityDiagnosis c where c.userId = :userId")
    Integer findMaxVersionByUserId(@Param("userId") Long userId);
}
