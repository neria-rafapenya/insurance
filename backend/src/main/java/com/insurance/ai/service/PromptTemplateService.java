package com.insurance.ai.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.insurance.model.PromptTemplate;
import com.insurance.repository.PromptTemplateRepository;

@Service
public class PromptTemplateService {
  private final PromptTemplateRepository promptTemplateRepository;

  public PromptTemplateService(PromptTemplateRepository promptTemplateRepository) {
    this.promptTemplateRepository = promptTemplateRepository;
  }

  public String resolve(
      String step,
      String templateKey,
      String language,
      String tipoSeguro,
      String fallback
  ) {
    String normalizedLang = language == null || language.isBlank()
        ? "es"
        : language.toLowerCase(Locale.ROOT);
    Optional<PromptTemplate> exact = fetch(step, templateKey, normalizedLang, tipoSeguro);
    if (exact.isPresent()) {
      return exact.get().getTemplate();
    }
    if (!"es".equals(normalizedLang)) {
      Optional<PromptTemplate> spanish = fetch(step, templateKey, "es", tipoSeguro);
      if (spanish.isPresent()) {
        return spanish.get().getTemplate();
      }
    }
    return fallback;
  }

  private Optional<PromptTemplate> fetch(
      String step,
      String templateKey,
      String language,
      String tipoSeguro
  ) {
    if (tipoSeguro != null && !tipoSeguro.isBlank()) {
      Optional<PromptTemplate> byTipo =
          promptTemplateRepository.findFirstByStepAndTemplateKeyAndLanguageAndTipoSeguroAndActivoTrue(
              step,
              templateKey,
              language,
              tipoSeguro
          );
      if (byTipo.isPresent()) {
        return byTipo;
      }
    }
    return promptTemplateRepository
        .findFirstByStepAndTemplateKeyAndLanguageAndTipoSeguroIsNullAndActivoTrue(
            step,
            templateKey,
            language
        );
  }
}
