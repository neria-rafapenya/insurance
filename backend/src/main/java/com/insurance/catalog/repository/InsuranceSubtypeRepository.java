package com.insurance.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.catalog.model.InsuranceSubtype;

public interface InsuranceSubtypeRepository extends JpaRepository<InsuranceSubtype, Integer> {
  List<InsuranceSubtype> findByTipoSeguro(String tipoSeguro);
}
