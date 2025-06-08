package xyz.mahmoudahmed.genbankinatorbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import xyz.mahmoudahmed.genbankinatorbackend.service.FileStorageService;
import xyz.mahmoudahmed.genbankinatorbackend.service.FileStorageService.StorageStats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Storage Admin", description = "Storage monitoring and administration")
@RequiredArgsConstructor
public class StorageAdminController {

    private final FileStorageService fileStorageService;

    @GetMapping("/storage/stats")
    @Operation(summary = "Get storage statistics",
            description = "Returns current storage usage and cache statistics")
    public ResponseEntity<StorageStats> getStorageStats() {
        StorageStats stats = fileStorageService.getStorageStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/storage/cleanup")
    @Operation(summary = "Manual cleanup",
            description = "Manually trigger cleanup of orphaned files")
    public ResponseEntity<CleanupResult> manualCleanup() {
        int orphanedFiles = fileStorageService.cleanupOrphanedFiles();
        return ResponseEntity.ok(new CleanupResult(orphanedFiles, "Cleanup completed"));
    }

    public static class CleanupResult {
        private final int filesDeleted;
        private final String message;

        public CleanupResult(int filesDeleted, String message) {
            this.filesDeleted = filesDeleted;
            this.message = message;
        }

        public int getFilesDeleted() { return filesDeleted; }
        public String getMessage() { return message; }
    }
}