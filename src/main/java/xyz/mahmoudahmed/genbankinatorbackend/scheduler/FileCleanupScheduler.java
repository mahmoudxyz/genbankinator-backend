package xyz.mahmoudahmed.genbankinatorbackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import lombok.extern.slf4j.Slf4j;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;
import xyz.mahmoudahmed.genbankinatorbackend.service.FileStorageService;

@Component
@Slf4j
public class FileCleanupScheduler {

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * Scheduled task that runs every hour to delete expired files
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void cleanupExpiredFiles() {
        log.info("Starting cleanup of expired files");

        List<FileMetadata> files = fileStorageService.getAllFiles();
        int deletedCount = 0;

        for (FileMetadata file : files) {
            if (file.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Deleting expired file: {} (UUID: {})", file.getOriginalFilename(), file.getUuid());
                fileStorageService.deleteFile(file.getUuid());
                deletedCount++;
            }
        }

        log.info("Cleanup completed. Deleted {} expired files", deletedCount);
    }
}