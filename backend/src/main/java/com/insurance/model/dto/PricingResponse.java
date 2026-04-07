package com.insurance.model.dto;

import java.util.List;

import com.insurance.model.QuoteStatus;

public record PricingResponse(
    QuoteStatus status,
    String segmento,
    Double basePrice,
    Double finalPrice,
    List<FactorDto> factors,
    List<String> messages,
    List<CoverageDto> coverages
) {}
