package xyz.mahmoudahmed.genbankinatorbackend.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionRequest;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionResponse;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;
import xyz.mahmoudahmed.genbankinatorbackend.service.GenbankService;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "GenBank Converter", description = "API for converting FASTA files to GenBank format")
public class GenbankController {

    private final GenbankService genbankService;
    private final ObjectMapper objectMapper;

    // Use constructor injection instead of @Autowired
    public GenbankController(GenbankService genbankService, ObjectMapper objectMapper) {
        this.genbankService = genbankService;
        this.objectMapper = objectMapper;
    }

    // Reduced response size for health check
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "API status")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert FASTA to GenBank", description = "Upload and convert files")
    @ApiResponse(responseCode = "200", description = "Success",
            content = @Content(schema = @Schema(implementation = ConversionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<ConversionResponse> convertFiles(
            @RequestPart("sequenceFile") MultipartFile sequenceFile,
            @RequestPart("annotationFile") MultipartFile annotationFile,
            @RequestPart("request") String requestJson) {

        ConversionRequest request;
        String clientId = null;

        try {
            JsonNode requestNode = objectMapper.readTree(requestJson);
            if (requestNode.has("clientId")) {
                clientId = requestNode.get("clientId").asText();
                ((ObjectNode) requestNode).remove("clientId");
                request = objectMapper.treeToValue(requestNode, ConversionRequest.class);
            } else {
                request = objectMapper.readValue(requestJson, ConversionRequest.class);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ConversionResponse.error("Invalid request format"));
        }

        // Quick validation
        if (!isValidFile(sequenceFile, "fasta") ||
                !isValidFile(annotationFile, request.getAnnotationFormat().toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body(ConversionResponse.error("Invalid file type"));
        }

        try {
            ConversionResponse response = genbankService.convertFiles(
                    request, sequenceFile, annotationFile, clientId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConversionResponse.error("Conversion failed"));
        }
    }

    @GetMapping("/files")
    @Operation(summary = "List files", description = "Get available files for client")
    public ResponseEntity<List<FileMetadata>> getAllFiles(
            @RequestParam(required = false) String clientId) {

        List<FileMetadata> files = genbankService.getAllFiles();

        if (clientId != null && !clientId.isEmpty()) {
            files = files.stream()
                    .filter(file -> clientId.equals(file.getClientId()))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/{uuid}")
    @Operation(summary = "Download file", description = "Download GenBank file by UUID")
    @ApiResponse(responseCode = "200", description = "File found")
    @ApiResponse(responseCode = "404", description = "Not found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String uuid,
            @RequestParam(required = false) String clientId) {

        try {
            FileMetadata metadata = genbankService.getFileMetadata(uuid);

            if (metadata == null) {
                return ResponseEntity.notFound().build();
            }

            // Check client access
            if (clientId != null && !clientId.isEmpty() &&
                    !clientId.equals(metadata.getClientId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            File file = genbankService.getFileByUuid(uuid);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(file.toPath());
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            String filename = extractFilename(file.getName());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"%s\"", filename))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileContent.length)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Simplified file validation
    private boolean isValidFile(MultipartFile file, String expectedType) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith("." + expectedType);
    }

    // Extract filename helper
    private String extractFilename(String fullName) {
        int underscoreIndex = fullName.indexOf("_");
        return underscoreIndex >= 0 ? fullName.substring(underscoreIndex + 1) : fullName;
    }
}