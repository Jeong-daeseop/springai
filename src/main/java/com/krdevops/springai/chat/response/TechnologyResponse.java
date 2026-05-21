package com.krdevops.springai.chat.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"name", "category", "description", "features", "useCases"})
public class TechnologyResponse {
    private String name;
    private String category;
    private String description;
    private List<String> features;
    private List<String> useCases;
}
