package com.insurance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.model.BasePrice;

public interface BasePriceRepository extends JpaRepository<BasePrice, Integer> {
  Optional<BasePrice> findByTipoSeguroAndSegmento(String tipoSeguro, String segmento);

  Optional<BasePrice> findFirstByTipoSeguroOrderByIdAsc(String tipoSeguro);
}
