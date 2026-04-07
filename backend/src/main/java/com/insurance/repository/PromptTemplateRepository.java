package com.insurance.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.PromptTemplate;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Integer> {
  Optional<PromptTemplate> findFirstByStepAndTemplateKeyAndLanguageAndTipoSeguroAndActivoTrue(
      String step,
      String templateKey,
      String language,
      String tipoSeguro
  );

  Optional<PromptTemplate> findFirstByStepAndTemplateKeyAndLanguageAndTipoSeguroIsNullAndActivoTrue(
      String step,
      String templateKey,
      String language
  );

  List<PromptTemplate> findByStepAndLanguageAndActivoTrue(String step, String language);
}
