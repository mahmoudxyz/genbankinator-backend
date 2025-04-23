package xyz.mahmoudahmed.genbankinatorbackend.service;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
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

    private Path fileStoragePath;

    @PostConstruct
    public void init() {
        this.fileStoragePath = Paths.get(fileStorageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStoragePath);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory where files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if (fileName.contains("..")) {
                throw new FileStorageException("Filename contains invalid path sequence: " + fileName);
            }

            // Generate unique ID for file
            String fileId = UUID.randomUUID().toString();
            Path targetLocation = this.fileStoragePath.resolve(fileId + "_" + fileName);

            // Copy file to the target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return targetLocation.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    public File getFileAsResource(String uuid) {
        try {
            Path filePath = findFileByUuid(uuid);
            return filePath.toFile();
        } catch (IOException ex) {
            throw new FileNotFoundException("File not found with UUID: " + uuid, ex);
        }
    }


    /**
     * Modified method to store GenBank results with client ID
     */
    public String storeGenbankResult(File file, String originalFilename, String clientId) {
        try {
            String uuid = UUID.randomUUID().toString();
            String extension = ".gb";
            Path targetLocation = this.fileStoragePath.resolve(uuid + "_" +
                    StringUtils.stripFilenameExtension(originalFilename) + extension);

            Files.copy(file.toPath(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Create metadata file with client ID
            FileMetadata metadata = new FileMetadata();
            metadata.setUuid(uuid);
            metadata.setOriginalFilename(originalFilename);
            metadata.setCreatedAt(LocalDateTime.now());
            metadata.setExpiresAt(LocalDateTime.now().plusHours(24));
            metadata.setFilePath(targetLocation.toString());
            metadata.setClientId(clientId); // Set the client ID

            Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
            Files.writeString(metadataPath, metadata.toJson());

            return uuid;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store result file", ex);
        }
    }

    /**
     * New method to get file metadata by UUID
     */
    public FileMetadata getFileMetadata(String uuid) {
        try {
            Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
            if (Files.exists(metadataPath)) {
                String content = Files.readString(metadataPath);
                return FileMetadata.fromJson(content);
            }
            return null;
        } catch (Exception e) {
            log.error("Error reading metadata for file with UUID: {}", uuid, e);
            return null;
        }
    }


    private Path findFileByUuid(String uuid) throws IOException {
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            return files
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().startsWith(uuid + "_"))
                    .findFirst()
                    .orElseThrow(() -> new FileNotFoundException("File not found with UUID: " + uuid));
        }
    }

    public List<FileMetadata> getAllFiles() {
        List<FileMetadata> metadataList = new ArrayList<>();
        try (Stream<Path> files = Files.list(this.fileStoragePath)) {
            List<Path> metadataFiles = files
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.toString().endsWith(".meta"))
                    .collect(Collectors.toList());

            for (Path metaPath : metadataFiles) {
                try {
                    String content = Files.readString(metaPath);
                    FileMetadata metadata = FileMetadata.fromJson(content);
                    metadataList.add(metadata);
                } catch (IOException e) {
                    log.error("Error reading metadata file: {}", metaPath, e);
                }
            }

            return metadataList;
        } catch (IOException e) {
            log.error("Error listing files", e);
            return List.of();
        }
    }

    public void deleteFile(String uuid) {
        try {
            // Delete the actual file
            Path filePath = findFileByUuid(uuid);
            Files.deleteIfExists(filePath);

            // Delete the metadata file
            Path metadataPath = this.fileStoragePath.resolve(uuid + ".meta");
            Files.deleteIfExists(metadataPath);

            log.info("Deleted file with UUID: {}", uuid);
        } catch (IOException e) {
            log.error("Error deleting file with UUID: {}", uuid, e);
        }
    }
}