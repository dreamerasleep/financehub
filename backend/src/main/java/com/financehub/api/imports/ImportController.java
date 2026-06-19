package com.financehub.api.imports;

import com.financehub.application.imports.ImportJobService;
import com.financehub.domain.imports.ImportJob;
import com.financehub.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PostMapping
    public ResponseEntity<ImportDtos.ImportJobResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file) throws IOException {
        ImportJob job = importJobService.upload(user.id(), file);
        return ResponseEntity.status(201).body(ImportDtos.ImportJobResponse.from(job));
    }

    @GetMapping
    public List<ImportDtos.ImportJobResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return importJobService.listRecent(user.id()).stream()
                .map(ImportDtos.ImportJobResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ImportDtos.ImportJobDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        ImportJob job = importJobService.get(user.id(), id);
        var rows = importJobService.getRows(user.id(), id).stream()
                .map(ImportDtos.ImportJobRowResponse::from)
                .toList();
        return new ImportDtos.ImportJobDetailResponse(
                ImportDtos.ImportJobResponse.from(job), rows);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        importJobService.cancel(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
