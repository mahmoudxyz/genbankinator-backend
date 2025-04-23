package xyz.mahmoudahmed.genbankinatorbackend.dto;



import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConversionRequest {

    @NotBlank(message = "Organism name is required")
    private String organism;

    @NotBlank(message = "Molecule type is required")
    private String moleculeType;

    @NotBlank(message = "Topology is required")
    private String topology;

    private String division;

    @NotBlank(message = "Annotation format is required")
    private String annotationFormat;

    // HeaderInfo fields
    private String definition;
    private String accessionNumber;
    private String version;
    private String keywords;
    private List<String> taxonomy;
    private Map<String, String> dbLinks;
    private List<ReferenceDto> references;
    private String comment;
    private Map<String, String> assemblyData;

    // TranslationOptions fields
    private Integer translTableNumber;
    private Boolean translateCDS;
    private Boolean includeStopCodon;
}