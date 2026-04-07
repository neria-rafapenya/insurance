package com.insurance.admin.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.model.PromptTemplate;
import com.insurance.model.dto.PromptTemplateDto;
import com.insurance.repository.PromptTemplateRepository;

@RestController
@RequestMapping("/admin/prompts")
public class PromptTemplateController {
  private final PromptTemplateRepository promptTemplateRepository;

  public PromptTemplateController(PromptTemplateRepository promptTemplateRepository) {
    this.promptTemplateRepository = promptTemplateRepository;
  }

  @GetMapping
  public List<PromptTemplateDto> list(
      @RequestParam(required = false) String step,
      @RequestParam(required = false) String language,
      @RequestParam(required = false) String tipoSeguro
  ) {
    List<PromptTemplate> templates = promptTemplateRepository.findAll();
    if (step != null && !step.isBlank()) {
      String filterStep = step.trim().toLowerCase(Locale.ROOT);
      templates = templates.stream()
          .filter(item -> filterStep.equalsIgnoreCase(item.getStep()))
          .toList();
    }
    if (language != null && !language.isBlank()) {
      String filterLang = language.trim().toLowerCase(Locale.ROOT);
      templates = templates.stream()
          .filter(item -> filterLang.equalsIgnoreCase(item.getLanguage()))
          .toList();
    }
    if (tipoSeguro != null && !tipoSeguro.isBlank()) {
      String filterTipo = tipoSeguro.trim().toLowerCase(Locale.ROOT);
      templates = templates.stream()
          .filter(item -> filterTipo.equalsIgnoreCase(item.getTipoSeguro()))
          .toList();
    }
    templates = templates.stream()
        .sorted(Comparator
            .comparing(PromptTemplate::getStep, Comparator.nullsLast(String::compareTo))
            .thenComparing(PromptTemplate::getTemplateKey, Comparator.nullsLast(String::compareTo))
            .thenComparing(PromptTemplate::getLanguage, Comparator.nullsLast(String::compareTo))
            .thenComparing(PromptTemplate::getTipoSeguro, Comparator.nullsLast(String::compareTo))
            .thenComparing(PromptTemplate::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    return templates.stream().map(this::toDto).toList();
  }

  @PostMapping
  public PromptTemplateDto create(@RequestBody PromptTemplateDto request) {
    PromptTemplate template = new PromptTemplate();
    applyToEntity(template, request);
    return toDto(promptTemplateRepository.save(template));
  }

  @PutMapping("/{id}")
  public PromptTemplateDto update(@PathVariable Integer id, @RequestBody PromptTemplateDto request) {
    PromptTemplate template = promptTemplateRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Prompt no encontrado"));
    applyToEntity(template, request);
    return toDto(promptTemplateRepository.save(template));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Integer id) {
    promptTemplateRepository.deleteById(id);
  }

  private void applyToEntity(PromptTemplate template, PromptTemplateDto dto) {
    template.setStep(normalizeBlank(dto.step()));
    template.setTemplateKey(normalizeBlank(dto.templateKey()));
    template.setLanguage(normalizeBlank(dto.language()));
    template.setTipoSeguro(normalizeBlank(dto.tipoSeguro()));
    template.setTemplate(dto.template());
    template.setActivo(dto.activo() == null ? Boolean.TRUE : dto.activo());
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

  private String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}
