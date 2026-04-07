package com.insurance.model.dto;

public record PromptTemplateDto(
    Integer id,
    String step,
    String templateKey,
    String language,
    String tipoSeguro,
    String template,
    Boolean activo
) {}
