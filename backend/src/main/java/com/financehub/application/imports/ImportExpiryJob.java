package com.financehub.application.imports;

import com.financehub.domain.imports.ImportJob;
import com.financehub.domain.imports.ImportJobRepository;
import com.financehub.domain.imports.ImportJobStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ImportExpiryJob {

    private final ImportJobRepository repository;

    public ImportExpiryJob(ImportJobRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${financehub.import.expiry-cron}")
    public void markExpired() {
        expireOnce(OffsetDateTime.now());
    }

    @Transactional
    public void expireOnce(OffsetDateTime now) {
        List<ImportJob> stale = repository.findByStatusAndExpiresAtBefore(
                ImportJobStatus.PENDING, now);
        for (ImportJob job : stale) {
            job.setStatus(ImportJobStatus.EXPIRED);
        }
    }
}
