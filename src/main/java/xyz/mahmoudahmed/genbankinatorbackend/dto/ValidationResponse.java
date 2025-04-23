package xyz.mahmoudahmed.genbankinatorbackend.dto;



import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a validation result.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of a file validation operation")
public class ValidationResponse {

    @Schema(description = "Whether the validation passed", example = "true")
    private boolean valid;

    @Schema(description = "The detected file format", example = "FASTA")
    private String detectedFormat;

    @Schema(description = "The number of sequences found", example = "5")
    private int sequenceCount;

    @Schema(description = "The number of features/annotations found", example = "42")
    private int featureCount;

    @Schema(description = "List of validation issues")
    @Builder.Default
    private List<Issue> issues = new ArrayList<>();

    @Schema(description = "Summary of the validation",
            example = "Found 5 sequences and 42 features. All sequences and features are valid.")
    private String summary;

    /**
     * Represents a validation issue.
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A validation issue found during file validation")
    public static class Issue {

        @Schema(description = "Issue type", example = "WARNING", allowableValues = {"ERROR", "WARNING"})
        private String type;

        @Schema(description = "Issue message", example = "Non-standard characters found in sequence")
        private String message;

        @Schema(description = "Location in the file", example = "Line 42")
        private String location;
    }
}