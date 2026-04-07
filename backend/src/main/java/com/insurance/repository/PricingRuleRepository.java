package com.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.PricingRule;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Integer> {
  List<PricingRule> findByTipoSeguro(String tipoSeguro);
}
