package com.insurance.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.catalog.model.InsuranceUsage;

public interface InsuranceUsageRepository extends JpaRepository<InsuranceUsage, Integer> {
  List<InsuranceUsage> findByTipoSeguro(String tipoSeguro);
}
