package com.insurance.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.insurance.auth.dto.AuthRequest;
import com.insurance.auth.dto.AuthResponse;
import com.insurance.auth.dto.RegisterRequest;
import com.insurance.auth.dto.UserDto;
import com.insurance.auth.model.AppUser;
import com.insurance.auth.model.UserRole;
import com.insurance.auth.repository.UserRepository;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya registrado");
    }

    AppUser user = new AppUser();
    user.setEmail(request.email());
    user.setFullName(request.fullName());
    user.setRole(UserRole.GUEST);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    AppUser saved = userRepository.save(user);

    String token = jwtService.generateToken(saved);
    long expiresAt = jwtService.getExpirationEpochSeconds(token);

    return new AuthResponse(token, expiresAt, toUserDto(saved));
  }

  public AuthResponse login(AuthRequest request) {
    AppUser user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    String token = jwtService.generateToken(user);
    long expiresAt = jwtService.getExpirationEpochSeconds(token);
    return new AuthResponse(token, expiresAt, toUserDto(user));
  }

  public UserDto toUserDto(AppUser user) {
    return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name().toLowerCase());
  }
}
