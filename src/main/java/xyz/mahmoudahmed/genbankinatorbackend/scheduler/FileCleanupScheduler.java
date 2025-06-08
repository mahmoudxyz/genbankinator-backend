package xyz.mahmoudahmed.genbankinatorbackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;
import xyz.mahmoudahmed.genbankinatorbackend.service.FileStorageService;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileStorageService fileStorageService;

    /**
     * Cleanup expired files every 4 hours instead of every hour
     * Runs at 2 AM, 6 AM, 10 AM, 2 PM, 6 PM, 10 PM
     */
    @Scheduled(cron = "0 0 2,6,10,14,18,22 * * *")
    public void cleanupExpiredFiles() {
        log.info("Starting cleanup of expired files");

        try {
            List<FileMetadata> expiredFiles = fileStorageService.getExpiredFiles(LocalDateTime.now());

            if (expiredFiles.isEmpty()) {
                log.info("No expired files found");
                return;
            }

            AtomicInteger deletedCount = new AtomicInteger(0);

            // Process files in batches to avoid memory issues
            expiredFiles.parallelStream()
                    .forEach(file -> {
                        try {
                            fileStorageService.deleteFile(file.getUuid());
                            deletedCount.incrementAndGet();
                            log.debug("Deleted expired file: {} (UUID: {})",
                                    file.getOriginalFilename(), file.getUuid());
                        } catch (Exception e) {
                            log.error("Failed to delete file {}: {}",
                                    file.getUuid(), e.getMessage());
                        }
                    });

            log.info("Cleanup completed. Deleted {} expired files", deletedCount.get());

        } catch (Exception e) {
            log.error("Error during file cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Weekly cleanup to remove orphaned files and optimize storage
     * Runs every Sunday at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void weeklyMaintenance() {
        log.info("Starting weekly maintenance");

        try {
            int orphanedFiles = fileStorageService.cleanupOrphanedFiles();
            log.info("Weekly maintenance completed. Cleaned {} orphaned files", orphanedFiles);
        } catch (Exception e) {
            log.error("Error during weekly maintenance: {}", e.getMessage(), e);
        }
    }
}