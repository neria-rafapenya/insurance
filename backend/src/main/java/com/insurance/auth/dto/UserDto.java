package com.insurance.auth.dto;

public record UserDto(
    Integer id,
    String email,
    String fullName,
    String role
) {}
