package com.insurance.project.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
    @NotBlank String nombre,
    String tipoSeguro
) {}
