package com.insurance.auth.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {
  @Override
  public String convertToDatabaseColumn(UserRole attribute) {
    return attribute == null ? null : attribute.name().toLowerCase();
  }

  @Override
  public UserRole convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return UserRole.GUEST;
    }
    return UserRole.valueOf(dbData.trim().toUpperCase());
  }
}
