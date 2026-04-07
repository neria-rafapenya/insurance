package com.insurance.project.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProjectStatusConverter implements AttributeConverter<ProjectStatus, String> {
  @Override
  public String convertToDatabaseColumn(ProjectStatus attribute) {
    return attribute == null ? null : attribute.name().toLowerCase();
  }

  @Override
  public ProjectStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return ProjectStatus.DRAFT;
    }
    return ProjectStatus.valueOf(dbData.trim().toUpperCase());
  }
}
