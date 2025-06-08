package xyz.mahmoudahmed.genbankinatorbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields to reduce response size
public class ConversionResponse {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("downloadUrl")
    private String downloadUrl;

    @JsonProperty("message")
    private String message;

    @JsonProperty("success")
    @Builder.Default
    private Boolean success = true;

    @JsonProperty("error")
    private String error;

    @JsonProperty("timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;

    @JsonProperty("fileSize")
    private Long fileSize;

    // Static factory methods for common responses
    public static ConversionResponse success(String uuid, String downloadUrl, String message) {
        return ConversionResponse.builder()
                .uuid(uuid)
                .downloadUrl(downloadUrl)
                .message(message)
                .success(true)
                .build();
    }

    public static ConversionResponse success(String uuid, String downloadUrl, String message,
                                             LocalDateTime expiresAt, Long fileSize) {
        return ConversionResponse.builder()
                .uuid(uuid)
                .downloadUrl(downloadUrl)
                .message(message)
                .success(true)
                .expiresAt(expiresAt)
                .fileSize(fileSize)
                .build();
    }

    public static ConversionResponse error(String errorMessage) {
        return ConversionResponse.builder()
                .success(false)
                .error(errorMessage)
                .message("Conversion failed")
                .build();
    }

    public static ConversionResponse error(String errorMessage, String details) {
        return ConversionResponse.builder()
                .success(false)
                .error(errorMessage)
                .message(details)
                .build();
    }

    // Utility methods
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }

    public boolean hasError() {
        return !isSuccessful();
    }

    // Helper method to get formatted file size
    public String getFormattedFileSize() {
        if (fileSize == null) return null;

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}