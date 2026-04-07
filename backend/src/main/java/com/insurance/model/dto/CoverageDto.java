package com.insurance.model.dto;

public record CoverageDto(
    String nombre,
    boolean incluido,
    String precioExtra,
    String descripcion
) {}
