package com.insurance.catalog.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import org.springframework.stereotype.Service;

import com.insurance.catalog.dto.CatalogOptionsResponse;
import com.insurance.catalog.model.InsuranceSubtype;
import com.insurance.catalog.model.InsuranceUsage;
import com.insurance.catalog.repository.InsuranceSubtypeRepository;
import com.insurance.catalog.repository.InsuranceUsageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.model.Coverage;
import com.insurance.model.BasePrice;
import com.insurance.model.dto.CoverageDto;
import com.insurance.catalog.dto.BasePriceResponse;
import com.insurance.repository.CoverageRepository;
import com.insurance.repository.BasePriceRepository;

@Service
public class CatalogService {
  private final InsuranceSubtypeRepository subtypeRepository;
  private final InsuranceUsageRepository usageRepository;
  private final CoverageRepository coverageRepository;
  private final BasePriceRepository basePriceRepository;
  private final ObjectMapper objectMapper;

  public CatalogService(
      InsuranceSubtypeRepository subtypeRepository,
      InsuranceUsageRepository usageRepository,
      CoverageRepository coverageRepository,
      BasePriceRepository basePriceRepository,
      ObjectMapper objectMapper
  ) {
    this.subtypeRepository = subtypeRepository;
    this.usageRepository = usageRepository;
    this.coverageRepository = coverageRepository;
    this.basePriceRepository = basePriceRepository;
    this.objectMapper = objectMapper;
  }

  public CatalogOptionsResponse getOptions() {
    Map<String, List<String>> subtypes = new LinkedHashMap<>();
    Map<String, Map<String, List<String>>> subtypeSynonyms = new LinkedHashMap<>();
    Map<String, String> subtypeQuestions = new LinkedHashMap<>();
    for (InsuranceSubtype subtype : subtypeRepository.findAll()) {
      subtypes.computeIfAbsent(subtype.getTipoSeguro(), key -> new ArrayList<>())
          .add(subtype.getNombre());
      if (StringUtils.hasText(subtype.getPreguntaAsistente())) {
        subtypeQuestions.putIfAbsent(subtype.getTipoSeguro(), subtype.getPreguntaAsistente());
      }
      if (StringUtils.hasText(subtype.getSinonimosJson())) {
        List<String> synonyms = parseSynonyms(subtype.getSinonimosJson());
        if (!synonyms.isEmpty()) {
          subtypeSynonyms
              .computeIfAbsent(subtype.getTipoSeguro(), key -> new LinkedHashMap<>())
              .put(subtype.getNombre(), synonyms);
        }
      }
    }

    Map<String, List<String>> usages = new LinkedHashMap<>();
    for (InsuranceUsage usage : usageRepository.findAll()) {
      usages.computeIfAbsent(usage.getTipoSeguro(), key -> new ArrayList<>())
          .add(usage.getNombre());
    }

    return new CatalogOptionsResponse(subtypes, usages, subtypeSynonyms, subtypeQuestions);
  }

  public List<CoverageDto> getCoverages(String tipoSeguro) {
    if (!StringUtils.hasText(tipoSeguro)) {
      return List.of();
    }
    String normalized = tipoSeguro.trim().toLowerCase();
    return coverageRepository.findByTipoSeguro(normalized).stream()
        .map(this::toCoverageDto)
        .toList();
  }

  public BasePriceResponse getBasePrice(String tipoSeguro) {
    if (!StringUtils.hasText(tipoSeguro)) {
      return null;
    }
    String normalized = tipoSeguro.trim().toLowerCase();
    BasePrice basePrice = basePriceRepository
        .findFirstByTipoSeguroOrderByIdAsc(normalized)
        .orElse(null);
    if (basePrice == null) {
      return null;
    }
    return new BasePriceResponse(basePrice.getPrecioBase(), basePrice.getSegmento());
  }

  private CoverageDto toCoverageDto(Coverage coverage) {
    return new CoverageDto(
        coverage.getNombre(),
        Boolean.TRUE.equals(coverage.getIncluido()),
        coverage.getPrecioExtra(),
        coverage.getDescripcion()
    );
  }

  private List<String> parseSynonyms(String json) {
    try {
      return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (Exception ex) {
      return List.of();
    }
  }
}
