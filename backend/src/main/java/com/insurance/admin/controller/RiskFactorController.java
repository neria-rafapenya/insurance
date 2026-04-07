package com.insurance.admin.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.model.RiskFactor;
import com.insurance.model.dto.RiskFactorDto;
import com.insurance.repository.RiskFactorRepository;

@RestController
@RequestMapping("/admin/risk-factors")
public class RiskFactorController {
  private final RiskFactorRepository riskFactorRepository;

  public RiskFactorController(RiskFactorRepository riskFactorRepository) {
    this.riskFactorRepository = riskFactorRepository;
  }

  @GetMapping
  public List<RiskFactorDto> list(@RequestParam(required = false) String tipo) {
    List<RiskFactor> factors;
    if (tipo == null || tipo.isBlank()) {
      factors = riskFactorRepository.findAll();
      factors.sort(Comparator
          .comparing(RiskFactor::getPrioridad, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(RiskFactor::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    } else {
      factors = riskFactorRepository
          .findByTipoSeguroOrTipoSeguroIsNullOrderByPrioridadDesc(tipo);
    }
    return factors.stream().map(this::toDto).toList();
  }

  @PostMapping
  public RiskFactorDto create(@RequestBody RiskFactorDto request) {
    RiskFactor factor = new RiskFactor();
    applyToEntity(factor, request);
    return toDto(riskFactorRepository.save(factor));
  }

  @PutMapping("/{id}")
  public RiskFactorDto update(@PathVariable Integer id, @RequestBody RiskFactorDto request) {
    RiskFactor factor = riskFactorRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado"));
    applyToEntity(factor, request);
    return toDto(riskFactorRepository.save(factor));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Integer id) {
    riskFactorRepository.deleteById(id);
  }

  private void applyToEntity(RiskFactor factor, RiskFactorDto dto) {
    factor.setTipoSeguro(normalizeBlank(dto.tipoSeguro()));
    factor.setCampo(dto.campo());
    factor.setFuente(dto.fuente());
    factor.setTipoMatch(dto.tipoMatch());
    factor.setValorMatch(dto.valorMatch());
    factor.setValorResultado(dto.valorResultado());
    factor.setPrioridad(dto.prioridad());
    factor.setActivo(dto.activo() == null ? Boolean.TRUE : dto.activo());
  }

  private RiskFactorDto toDto(RiskFactor factor) {
    return new RiskFactorDto(
        factor.getId(),
        factor.getTipoSeguro(),
        factor.getCampo(),
        factor.getFuente(),
        factor.getTipoMatch(),
        factor.getValorMatch(),
        factor.getValorResultado(),
        factor.getPrioridad(),
        factor.getActivo()
    );
  }

  private String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isBlank() ? null : trimmed;
  }
}
