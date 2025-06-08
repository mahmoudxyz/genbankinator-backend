package xyz.mahmoudahmed.genbankinatorbackend.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import xyz.mahmoudahmed.genbankinatorbackend.exception.FileNotFoundException;
import xyz.mahmoudahmed.genbankinatorbackend.exception.FileStorageException;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.storage.location:temp-files}")
    private String fileStorageLocation;

    @Value("${file.retention.hours:24}")
    private int fileRetentionHours;

    private Path fileStoragePath;

    // In-memory cache for frequently accessed metadata
    private final ConcurrentHashMap<String, FileMetadata> metadataCache = new ConcurrentHashMap<>();

    // Maximum cache size to prevent memory issues
    private static final int MAX_CACHE_SIZE = 1000;

    @PostConstruct
    public void init() {
        this.fileStoragePath = Paths.get(fileStorageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStoragePath);
            log.info("Initialized file storage at: {}", this.fileStoragePath);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create file storage directory", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        // Quick validation
        if (fileName.contains("..")) {
            throw new FileStorageException("Invalid filename: " + fileName);
        }

        // Check file size before processing
        if (file.getSize() > 20 * 1024 * 1024) { // 20MB limit
            throw new FileStorageException("File too large: " + file.getSize());
        }

        try {
            String fileId = UUID.randomUUID().toString();
            Path targetLocation = this.fileStoragePath.resolve(fileId + "_" + fileName);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    public File getFileAsResource(String uuid) {
        try {
            Path filePath = findFileByUuid(uuid);
            File file = filePath.toFile();

            // Check if file exists and is readable
            if (!file.exists() || !file.canRead()) {
                throw new FileNotFoundException("File not accessible: " + uuid);
            }

            return file;
        } catch (IOException ex) {
            throw new FileNotFoundException("File not found: " + uuid, ex);
        }
    }

    /**
     * Optimized method to store GenBank results with efficient metadata handling
     */
    public String storeGenbankResult(File file, String originalFilename, String clientId) {
        try {
            String uuid = UUID.randomUUID().toString();
            String cleanFilename = StringUtils.stripFilenameExtension(originalFilename) + ".gb";
            Path targetLocation = this.fileStoragePath.resolve(uuid + "_" + cleanFilename);

            // Use move instead of copy for better performance
            Files.move(file.toPath(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Create metadata
            FileMetadata metadata = createFileMetadata(uuid, originalFilename,
                    targetLocation.toString(), clientId);

            // Store metadata efficiently
            storeMetadata(uuid, metadata);

            log.debug("Stored GenBank result: {} for client: {}", uuid, clientId);
            return uuid;

        } catch (IOException ex) {
            throw new FileStorageException("Could not store result file", ex);
        }
    }

    /**
     * Cached metadata retrieval for better performance
     */
    @Cacheable(value = "metadata", key = "#uuid")
    public FileMetadata getFileMetadata(String uuid) {
        // Check in-memory cache first
        FileMetadata cached = metadataCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        try {
            Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
            if (Files.exists(metadataPath)) {
                String content = Files.readString(metadataPath);
                FileMetadata metadata = FileMetadata.fromJson(content);

                // Add to cache if not full
                if (metadataCache.size() < MAX_CACHE_SIZE) {
                    metadataCache.put(uuid, metadata);
                }

                return metadata;
            }
            return null;
        } catch (Exception e) {
            log.error("Error reading metadata for UUID: {}", uuid, e);
            return null;
        }
    }

    /**
     * Optimized method to get only expired files
     */
    public List<FileMetadata> getExpiredFiles(LocalDateTime now) {
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            return files
                    .filter(file -> file.toString().endsWith(".meta"))
                    .parallel() // Use parallel processing for better performance
                    .map(this::loadMetadataFromPath)
                    .filter(metadata -> metadata != null && metadata.getExpiresAt().isBefore(now))
                    .toList();
        } catch (IOException e) {
            log.error("Error listing expired files", e);
            return List.of();
        }
    }

    /**
     * Optimized file listing with streaming
     */
    public List<FileMetadata> getAllFiles() {
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            return files
                    .filter(file -> file.toString().endsWith(".meta"))
                    .parallel()
                    .map(this::loadMetadataFromPath)
                    .filter(metadata -> metadata != null)
                    .toList();
        } catch (IOException e) {
            log.error("Error listing all files", e);
            return List.of();
        }
    }

    /**
     * Enhanced delete with cache cleanup
     */
    @CacheEvict(value = "metadata", key = "#uuid")
    public void deleteFile(String uuid) {
        try {
            // Remove from cache first
            metadataCache.remove(uuid);

            // Delete the actual file
            Path filePath = findFileByUuid(uuid);
            Files.deleteIfExists(filePath);

            // Delete the metadata file
            Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
            Files.deleteIfExists(metadataPath);

            log.debug("Deleted file: {}", uuid);
        } catch (IOException e) {
            log.error("Error deleting file: {}", uuid, e);
        }
    }

    /**
     * New method to cleanup orphaned files
     */
    public int cleanupOrphanedFiles() {
        int deletedCount = 0;
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            List<Path> allFiles = files.toList();

            for (Path file : allFiles) {
                String fileName = file.getFileName().toString();

                // Check for files without corresponding metadata
                if (!fileName.endsWith(".meta") && fileName.contains("_")) {
                    String uuid = fileName.substring(0, fileName.indexOf("_"));
                    Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");

                    if (!Files.exists(metadataPath)) {
                        Files.deleteIfExists(file);
                        deletedCount++;
                        log.debug("Deleted orphaned file: {}", fileName);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error during orphaned file cleanup", e);
        }
        return deletedCount;
    }

    /**
     * Get storage statistics for monitoring
     */
    public StorageStats getStorageStats() {
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            List<Path> allFiles = files.toList();

            long totalFiles = allFiles.stream()
                    .filter(file -> !file.toString().endsWith(".meta"))
                    .count();

            long totalSize = allFiles.stream()
                    .filter(file -> !file.toString().endsWith(".meta"))
                    .mapToLong(file -> {
                        try {
                            return Files.size(file);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

            return new StorageStats(totalFiles, totalSize, metadataCache.size());
        } catch (IOException e) {
            log.error("Error getting storage stats", e);
            return new StorageStats(0, 0, metadataCache.size());
        }
    }

    // Helper methods
    private Path findFileByUuid(String uuid) throws IOException {
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            return files
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().startsWith(uuid + "_"))
                    .findFirst()
                    .orElseThrow(() -> new FileNotFoundException("File not found: " + uuid));
        }
    }

    private FileMetadata createFileMetadata(String uuid, String originalFilename,
                                            String filePath, String clientId) {
        FileMetadata metadata = new FileMetadata();
        metadata.setUuid(uuid);
        metadata.setOriginalFilename(originalFilename);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setExpiresAt(LocalDateTime.now().plusHours(fileRetentionHours));
        metadata.setFilePath(filePath);
        metadata.setClientId(clientId);
        return metadata;
    }

    private void storeMetadata(String uuid, FileMetadata metadata) throws IOException {
        Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
        Files.writeString(metadataPath, metadata.toJson());

        // Add to cache if not full
        if (metadataCache.size() < MAX_CACHE_SIZE) {
            metadataCache.put(uuid, metadata);
        }
    }

    private FileMetadata loadMetadataFromPath(Path metaPath) {
        try {
            String content = Files.readString(metaPath);
            return FileMetadata.fromJson(content);
        } catch (IOException e) {
            log.warn("Failed to read metadata: {}", metaPath.getFileName());
            return null;
        }
    }

    // Storage statistics inner class
    public static class StorageStats {
        private final long fileCount;
        private final long totalSize;
        private final int cacheSize;

        public StorageStats(long fileCount, long totalSize, int cacheSize) {
            this.fileCount = fileCount;
            this.totalSize = totalSize;
            this.cacheSize = cacheSize;
        }

        public long getFileCount() { return fileCount; }
        public long getTotalSize() { return totalSize; }
        public int getCacheSize() { return cacheSize; }
        public String getTotalSizeFormatted() {
            return String.format("%.2f MB", totalSize / (1024.0 * 1024.0));
        }
    }
}