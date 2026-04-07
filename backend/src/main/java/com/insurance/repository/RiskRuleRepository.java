package com.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.RiskRule;

public interface RiskRuleRepository extends JpaRepository<RiskRule, Integer> {
  List<RiskRule> findByTipoSeguro(String tipoSeguro);
}
