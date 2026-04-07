package com.insurance.catalog.dto;

import java.util.List;
import java.util.Map;

public record CatalogOptionsResponse(
    Map<String, List<String>> subtypes,
    Map<String, List<String>> usages,
    Map<String, Map<String, List<String>>> subtypeSynonyms,
    Map<String, String> subtypeQuestions
) {}
