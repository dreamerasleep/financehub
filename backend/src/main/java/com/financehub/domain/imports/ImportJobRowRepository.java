package com.financehub.domain.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {

    List<ImportJobRow> findByJobIdOrderByRowIndexAsc(Long jobId);

    Optional<ImportJobRow> findByIdAndJobId(Long id, Long jobId);

    long countByJobIdAndStatus(Long jobId, ImportJobRowStatus status);

    @Query("""
        SELECT r.dedupHash FROM ImportJobRow r
        WHERE r.jobId = :jobId
          AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
          AND r.id <> :excludeRowId
          AND r.dedupHash IS NOT NULL
    """)
    Set<String> findOkDedupHashesByJobIdExcept(
            @Param("jobId") Long jobId,
            @Param("excludeRowId") Long excludeRowId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM ImportJobRow r
        WHERE r.jobId = :jobId AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
        ORDER BY r.rowIndex ASC
    """)
    List<ImportJobRow> lockOkRowsByJobId(@Param("jobId") Long jobId);
}
