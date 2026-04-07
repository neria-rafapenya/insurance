package com.insurance.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.insurance.catalog.dto.BasePriceResponse;
import com.insurance.catalog.dto.CatalogOptionsResponse;
import com.insurance.catalog.service.CatalogService;
import com.insurance.model.PromptTemplate;
import com.insurance.model.dto.CoverageDto;
import com.insurance.model.dto.PromptTemplateDto;
import com.insurance.repository.PromptTemplateRepository;

import java.util.List;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
  private final CatalogService catalogService;
  private final PromptTemplateRepository promptTemplateRepository;

  public CatalogController(
      CatalogService catalogService,
      PromptTemplateRepository promptTemplateRepository
  ) {
    this.catalogService = catalogService;
    this.promptTemplateRepository = promptTemplateRepository;
  }

  @GetMapping("/options")
  public CatalogOptionsResponse getOptions() {
    return catalogService.getOptions();
  }

  @GetMapping("/coverages")
  public List<CoverageDto> getCoverages(
      @RequestParam(name = "tipo", required = false) String tipo,
      @RequestParam(name = "tipo_seguro", required = false) String tipoSeguro,
      @RequestParam(name = "tipoSeguro", required = false) String tipoSeguroCamel
  ) {
    String resolved = firstNonBlank(tipo, tipoSeguro, tipoSeguroCamel);
    if (resolved == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_seguro es requerido");
    }
    return catalogService.getCoverages(resolved);
  }

  @GetMapping("/base-price")
  public BasePriceResponse getBasePrice(
      @RequestParam(name = "tipo_seguro", required = false) String tipoSeguro
  ) {
    if (tipoSeguro == null || tipoSeguro.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo_seguro es requerido");
    }
    BasePriceResponse response = catalogService.getBasePrice(tipoSeguro);
    if (response == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay precio base");
    }
    return response;
  }

  @GetMapping("/prompts")
  public List<PromptTemplateDto> getPrompts(
      @RequestParam(name = "step") String step,
      @RequestParam(name = "language", required = false) String language,
      @RequestParam(name = "tipo_seguro", required = false) String tipoSeguro
  ) {
    if (step == null || step.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "step es requerido");
    }
    String resolvedLanguage = language == null || language.isBlank()
        ? "es"
        : language.trim().toLowerCase();
    List<PromptTemplate> templates =
        promptTemplateRepository.findByStepAndLanguageAndActivoTrue(step, resolvedLanguage);
    if (templates.isEmpty() && !"es".equals(resolvedLanguage)) {
      templates = promptTemplateRepository.findByStepAndLanguageAndActivoTrue(step, "es");
    }
    return resolveTemplates(templates, tipoSeguro).stream()
        .map(this::toDto)
        .toList();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private List<PromptTemplate> resolveTemplates(List<PromptTemplate> templates, String tipoSeguro) {
    if (templates == null || templates.isEmpty()) {
      return List.of();
    }
    String tipo = tipoSeguro == null || tipoSeguro.isBlank()
        ? null
        : tipoSeguro.trim().toLowerCase();
    java.util.Map<String, PromptTemplate> resolved = new java.util.LinkedHashMap<>();
    templates.stream()
        .filter(item -> tipo == null
            || item.getTipoSeguro() == null
            || item.getTipoSeguro().equalsIgnoreCase(tipo))
        .sorted(java.util.Comparator.comparing(PromptTemplate::getTemplateKey))
        .forEach(item -> {
          String key = item.getTemplateKey();
          if (key == null) {
            return;
          }
          boolean isTipoMatch = tipo != null
              && item.getTipoSeguro() != null
              && item.getTipoSeguro().equalsIgnoreCase(tipo);
          if (isTipoMatch || !resolved.containsKey(key)) {
            resolved.put(key, item);
          }
        });
    return new java.util.ArrayList<>(resolved.values());
  }

  private PromptTemplateDto toDto(PromptTemplate template) {
    return new PromptTemplateDto(
        template.getId(),
        template.getStep(),
        template.getTemplateKey(),
        template.getLanguage(),
        template.getTipoSeguro(),
        template.getTemplate(),
        template.getActivo()
    );
  }
}
