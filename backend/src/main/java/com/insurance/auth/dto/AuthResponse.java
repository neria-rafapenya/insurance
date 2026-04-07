package com.insurance.auth.dto;

public record AuthResponse(
    String token,
    long expiresAt,
    UserDto user
) {}
