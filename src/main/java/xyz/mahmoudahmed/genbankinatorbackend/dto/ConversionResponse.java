package xyz.mahmoudahmed.genbankinatorbackend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversionResponse {
    private String uuid;
    private String downloadUrl;
    private String message;
}