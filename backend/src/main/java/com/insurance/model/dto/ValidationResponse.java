package com.insurance.model.dto;

import java.util.List;

import com.insurance.model.QuoteStatus;

public record ValidationResponse(
    QuoteStatus status,
    List<String> messages,
    List<String> surcharges
) {}
