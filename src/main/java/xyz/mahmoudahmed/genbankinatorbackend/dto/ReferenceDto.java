package xyz.mahmoudahmed.genbankinatorbackend.dto;

import java.util.List;

import lombok.Data;

@Data
public class ReferenceDto {
    private List<String> authors;
    private String title;
    private String journal;
    private String pubStatus;
}