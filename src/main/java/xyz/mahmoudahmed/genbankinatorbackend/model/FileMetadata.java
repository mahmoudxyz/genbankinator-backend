package xyz.mahmoudahmed.genbankinatorbackend.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMetadata {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    static {
        // Configure the mapper to ignore unknown properties
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private String clientId;
    private String uuid;
    private String originalFilename;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @JsonIgnore
    private String filePath;

    @JsonProperty("downloadUrl")
    public String getDownloadUrl() {
        return "/api/v1/files/" + uuid;
    }

    @JsonProperty("expired")
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    @JsonProperty(value = "timeToExpiry", access = JsonProperty.Access.READ_ONLY)
    public String getTimeToExpiry() {
        if (isExpired()) {
            return "Expired";
        }
        long hours = java.time.Duration.between(LocalDateTime.now(), expiresAt).toHours();
        return hours + " hours";
    }

    public String toJson() {
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting metadata to JSON", e);
        }
    }

    public static FileMetadata fromJson(String json) {
        try {
            return mapper.readValue(json, FileMetadata.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error reading metadata from JSON", e);
        }
    }
}