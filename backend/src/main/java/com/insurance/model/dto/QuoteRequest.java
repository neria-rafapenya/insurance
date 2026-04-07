package com.insurance.model.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record QuoteRequest(
    @NotBlank String tipoSeguro,
    Map<String, Object> data
) {}
