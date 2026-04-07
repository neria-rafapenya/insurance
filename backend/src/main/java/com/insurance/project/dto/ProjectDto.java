package com.insurance.project.dto;

public record ProjectDto(
    Integer id,
    String nombre,
    String tipoSeguro,
    String estado,
    Integer currentStep
) {}
