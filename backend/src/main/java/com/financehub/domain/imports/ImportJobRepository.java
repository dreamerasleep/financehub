package com.financehub.domain.imports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    Optional<ImportJob> findByIdAndUserId(Long id, Long userId);

    List<ImportJob> findTop20ByUserIdOrderByIdDesc(Long userId);

    List<ImportJob> findByStatusAndExpiresAtBefore(ImportJobStatus status, OffsetDateTime when);
}
