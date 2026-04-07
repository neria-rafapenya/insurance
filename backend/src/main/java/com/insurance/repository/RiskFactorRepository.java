package com.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.RiskFactor;

public interface RiskFactorRepository extends JpaRepository<RiskFactor, Integer> {
  List<RiskFactor> findByTipoSeguroOrTipoSeguroIsNullOrderByPrioridadDesc(String tipoSeguro);
}
