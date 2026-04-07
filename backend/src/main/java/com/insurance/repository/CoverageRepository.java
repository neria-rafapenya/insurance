package com.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.Coverage;

public interface CoverageRepository extends JpaRepository<Coverage, Integer> {
  List<Coverage> findByTipoSeguro(String tipoSeguro);
}
