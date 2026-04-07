package com.insurance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Integer> {
  List<InsuranceProduct> findByActivoTrue();
}
