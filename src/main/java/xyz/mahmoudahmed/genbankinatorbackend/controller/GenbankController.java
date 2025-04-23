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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionRequest;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionResponse;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;
import xyz.mahmoudahmed.genbankinatorbackend.service.GenbankService;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "GenBank Converter", description = "API for converting FASTA files to GenBank format")
public class GenbankController {

    // Simple health check endpoint
    @GetMapping("/health")
    @Operation(summary = "Health check endpoint", description = "Verify that the API is up and running")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "GenBankinator API is running");
        return ResponseEntity.ok(response);
    }

    @Autowired
    private GenbankService genbankService;



    @PostMapping(value = "/convert")
    @Operation(summary = "Convert FASTA files to GenBank format",
            description = "Upload sequence and annotation files with conversion options")
    @ApiResponse(responseCode = "200", description = "Conversion successful",
            content = @Content(schema = @Schema(implementation = ConversionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request or files")
    public ResponseEntity<ConversionResponse> convertFiles(
            @RequestPart(value = "sequenceFile", required = true) MultipartFile sequenceFile,
            @RequestPart(value = "annotationFile", required = true) MultipartFile annotationFile,
            @RequestPart(value = "request", required = true) String requestJson) {

        // Parse the request JSON string to your request object
        ConversionRequest request;
        String clientId = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            // First try to parse as JsonNode to extract clientId
            JsonNode requestNode = objectMapper.readTree(requestJson);

            // Check if clientId is present
            if (requestNode.has("clientId")) {
                clientId = requestNode.get("clientId").asText();

                // Remove clientId from the node for further processing
                ((ObjectNode) requestNode).remove("clientId");

                // Convert modified node to request object
                request = objectMapper.treeToValue(requestNode, ConversionRequest.class);
            } else {
                // No clientId, just parse as is
                request = objectMapper.readValue(requestJson, ConversionRequest.class);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request format: " + e.getMessage(), e);
        }

        // Validate file types
        validateFileType(sequenceFile, "fasta");
        validateFileType(annotationFile, request.getAnnotationFormat().toLowerCase());

        // Pass clientId to the service
        ConversionResponse response = genbankService.convertFiles(
                request,
                sequenceFile,
                annotationFile,
                clientId  // Pass the client ID
        );

        return ResponseEntity.ok(response);
    }


    private void validateRequest(ConversionRequest request) {
        if (request.getOrganism() == null || request.getOrganism().trim().isEmpty()) {
            throw new IllegalArgumentException("Organism name is required");
        }
        if (request.getMoleculeType() == null || request.getMoleculeType().trim().isEmpty()) {
            throw new IllegalArgumentException("Molecule type is required");
        }
        if (request.getTopology() == null || request.getTopology().trim().isEmpty()) {
            throw new IllegalArgumentException("Topology is required");
        }
        if (request.getAnnotationFormat() == null || request.getAnnotationFormat().trim().isEmpty()) {
            throw new IllegalArgumentException("Annotation format is required");
        }
    }

    /**
     * Updates the file listing method to filter by clientId
     */
    @GetMapping("/files")
    @Operation(summary = "List all available files",
            description = "Returns a list of all available converted files for the current client")
    public ResponseEntity<List<FileMetadata>> getAllFiles(@RequestParam(required = false) String clientId) {
        List<FileMetadata> files = genbankService.getAllFiles();

        // Filter files by clientId if provided
        if (clientId != null && !clientId.isEmpty()) {
            files = files.stream()
                    .filter(file -> {
                        // Check if this file belongs to the client
                        // This assumes you've added a clientId field to your FileMetadata class
                        return clientId.equals(file.getClientId());
                    })
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(files);
    }

    /**
     * Updates the file download method to check clientId
     */
    @GetMapping("/files/{uuid}")
    @Operation(summary = "Download a converted file",
            description = "Download a previously converted GenBank file by UUID")
    @ApiResponse(responseCode = "200", description = "File found")
    @ApiResponse(responseCode = "404", description = "File not found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String uuid,
            @RequestParam(required = false) String clientId) throws IOException {

        // Get the file metadata first
        FileMetadata metadata = genbankService.getFileMetadata(uuid);

        // If client ID is provided, verify it matches
        if (clientId != null && !clientId.isEmpty() && metadata != null) {
            if (!clientId.equals(metadata.getClientId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ByteArrayResource("Access denied".getBytes()));
            }
        }

        File file = genbankService.getFileByUuid(uuid);

        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(file.toPath()));

        // Get the original filename from the file
        String filename = file.getName();
        if (filename.contains("_")) {
            filename = filename.substring(filename.indexOf("_") + 1);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }

    private void validateFileType(MultipartFile file, String expectedType) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith("." + expectedType)) {
            throw new IllegalArgumentException("File must be of type " + expectedType +
                    ". Provided file appears to be: " + (filename != null ?
                    filename.substring(filename.lastIndexOf('.') + 1) : "unknown"));
        }
    }
}