package xyz.mahmoudahmed.genbankinatorbackend.service;



import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;




import lombok.extern.slf4j.Slf4j;
import xyz.mahmoudahmed.converter.GenbankConverter;
import xyz.mahmoudahmed.exception.ConversionException;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionRequest;
import xyz.mahmoudahmed.genbankinatorbackend.dto.ConversionResponse;
import xyz.mahmoudahmed.genbankinatorbackend.model.FileMetadata;
import xyz.mahmoudahmed.model.*;
import xyz.mahmoudahmed.parsers.FastaAnnotationParser;

@Service
@Slf4j
public class GenbankService {

    @Autowired
    private FileStorageService fileStorageService;

    public ConversionResponse convertFiles(ConversionRequest request,
                                           MultipartFile sequenceFile,
                                           MultipartFile annotationFile,
                                           String clientId) {
        try {
            // Store uploaded files
            String sequencePath = fileStorageService.storeFile(sequenceFile);
            String annotationPath = fileStorageService.storeFile(annotationFile);

            // Create converter instance using builder
            GenbankConverter converter = GenbankConverter.builder()
                    .withAnnotationParser(new FastaAnnotationParser())
                    .build();

            // Create reference information if provided
            List<ReferenceInfo> references = new ArrayList<>();
            if (request.getReferences() != null && !request.getReferences().isEmpty()) {
                for (int i = 0; i < request.getReferences().size(); i++) {
                    var ref = request.getReferences().get(i);
                    ReferenceInfo reference = ReferenceInfo.builder()
                            .number(i + 1)
                            .authors(ref.getAuthors())
                            .title(ref.getTitle())
                            .journal(ref.getJournal())
                            .pubStatus(ref.getPubStatus())
                            .build();
                    references.add(reference);
                }
            }

            // Create header info using data from request
            HeaderInfo.Builder headerBuilder = HeaderInfo.builder();
            if (request.getDefinition() != null) {
                headerBuilder.definition(request.getDefinition());
            }
            if (request.getAccessionNumber() != null) {
                headerBuilder.accessionNumber(request.getAccessionNumber());
            }
            if (request.getVersion() != null) {
                headerBuilder.version(request.getVersion());
            }
            if (request.getKeywords() != null) {
                headerBuilder.keywords(request.getKeywords());
            }
            if (request.getTaxonomy() != null) {
                headerBuilder.taxonomy(request.getTaxonomy());
            }
            if (request.getDbLinks() != null) {
                headerBuilder.dbLinks(request.getDbLinks());
            }
            if (!references.isEmpty()) {
                headerBuilder.references(references);
            }
            if (request.getComment() != null) {
                headerBuilder.comment(request.getComment());
            }
            if (request.getAssemblyData() != null) {
                headerBuilder.assemblyData(request.getAssemblyData());
            }

            HeaderInfo headerInfo = headerBuilder.build();

            // Set up translation options
            TranslationOptions.Builder translationBuilder = TranslationOptions.builder();
            if (request.getTranslTableNumber() != null) {
                translationBuilder.translTableNumber(request.getTranslTableNumber());
            }
            if (request.getTranslateCDS() != null) {
                translationBuilder.translateCDS(request.getTranslateCDS());
            }
            if (request.getIncludeStopCodon() != null) {
                translationBuilder.includeStopCodon(request.getIncludeStopCodon());
            }

            TranslationOptions translationOptions = translationBuilder.build();

            // Configure conversion options
            ConversionOptions options = ConversionOptions.builder()
                    .organism(request.getOrganism())
                    .moleculeType(request.getMoleculeType())
                    .topology(request.getTopology())
                    .division(request.getDivision())
                    .annotationFormat(request.getAnnotationFormat())
                    .headerInfo(headerInfo)
                    .translationOptions(translationOptions)
                    .build();

            // Convert files
            GenbankResult result = converter.convert(
                    new File(sequencePath),
                    new File(annotationPath),
                    options);

            // Create a temporary file for the result
            File outputFile = File.createTempFile("genbank-result-", ".gb");
            result.writeToFile(outputFile);

            // Store the file for 24 hours
            String uuid = fileStorageService.storeGenbankResult(
                    outputFile,
                    sequenceFile.getOriginalFilename(),
                    clientId   // Include the client ID
            );


            // Clean up temporary files
            outputFile.delete();

            // Return response with file UUID and download URL
            return ConversionResponse.builder()
                    .uuid(uuid)
                    .downloadUrl("/api/v1/files/" + uuid)
                    .message("Conversion successful. File will be available for 24 hours.")
                    .build();
        } catch (Exception e) {
            log.error("Error during conversion", e);
            throw new ConversionException("Failed to convert files: " + e.getMessage(), e);
        }
    }

    public List<FileMetadata> getAllFiles() {
        return fileStorageService.getAllFiles();
    }

    public File getFileByUuid(String uuid) {
        return fileStorageService.getFileAsResource(uuid);
    }

    /**
     * New method to get file metadata
     */
    public FileMetadata getFileMetadata(String uuid) {
        return fileStorageService.getFileMetadata(uuid);
    }

}