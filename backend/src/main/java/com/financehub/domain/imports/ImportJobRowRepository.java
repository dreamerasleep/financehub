package com.financehub.domain.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {

    List<ImportJobRow> findByJobIdOrderByRowIndexAsc(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM ImportJobRow r
        WHERE r.jobId = :jobId AND r.status = com.financehub.domain.imports.ImportJobRowStatus.OK
        ORDER BY r.rowIndex ASC
    """)
    List<ImportJobRow> lockOkRowsByJobId(@Param("jobId") Long jobId);
}
