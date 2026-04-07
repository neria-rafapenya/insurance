package com.insurance.model.dto;

import java.util.List;

public record InitResponse(
    List<ProductDto> products
) {}
