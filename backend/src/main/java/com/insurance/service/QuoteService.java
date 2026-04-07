package com.insurance.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.model.BasePrice;
import com.insurance.model.Coverage;
import com.insurance.model.InsuranceProduct;
import com.insurance.model.PricingRule;
import com.insurance.model.QuoteStatus;
import com.insurance.model.RiskRule;
import com.insurance.model.dto.CoverageDto;
import com.insurance.model.dto.FactorDto;
import com.insurance.model.dto.InitResponse;
import com.insurance.model.dto.PricingResponse;
import com.insurance.model.dto.ProductDto;
import com.insurance.model.dto.QuoteRequest;
import com.insurance.model.dto.ValidationResponse;
import com.insurance.repository.BasePriceRepository;
import com.insurance.repository.CoverageRepository;
import com.insurance.repository.InsuranceProductRepository;
import com.insurance.repository.PricingRuleRepository;
import com.insurance.repository.RiskRuleRepository;

@Service
public class QuoteService {
  private static final double SURCHARGE_FACTOR = 1.1;

  private final BasePriceRepository basePriceRepository;
  private final CoverageRepository coverageRepository;
  private final InsuranceProductRepository insuranceProductRepository;
  private final PricingRuleRepository pricingRuleRepository;
  private final RiskRuleRepository riskRuleRepository;
  private final ObjectMapper objectMapper;

  public QuoteService(
      BasePriceRepository basePriceRepository,
      CoverageRepository coverageRepository,
      InsuranceProductRepository insuranceProductRepository,
      PricingRuleRepository pricingRuleRepository,
      RiskRuleRepository riskRuleRepository,
      ObjectMapper objectMapper
  ) {
    this.basePriceRepository = basePriceRepository;
    this.coverageRepository = coverageRepository;
    this.insuranceProductRepository = insuranceProductRepository;
    this.pricingRuleRepository = pricingRuleRepository;
    this.riskRuleRepository = riskRuleRepository;
    this.objectMapper = objectMapper;
  }

  public InitResponse init() {
    List<ProductDto> products = insuranceProductRepository.findByActivoTrue().stream()
        .map(this::toProductDto)
        .toList();
    return new InitResponse(products);
  }

  public ValidationResponse validate(QuoteRequest request) {
    ValidationOutcome outcome = evaluateRiskRules(request.tipoSeguro(), safeData(request.data()));
    return new ValidationResponse(outcome.status, outcome.messages, outcome.surcharges);
  }

  public PricingResponse calculate(QuoteRequest request) {
    Map<String, Object> data = safeData(request.data());
    ValidationOutcome outcome = evaluateRiskRules(request.tipoSeguro(), data);

    List<CoverageDto> coverages = coverageRepository.findByTipoSeguro(request.tipoSeguro()).stream()
        .map(this::toCoverageDto)
        .toList();

    if (outcome.status == QuoteStatus.REJECT) {
      return new PricingResponse(outcome.status, null, null, null, List.of(), outcome.messages, coverages);
    }

    String segmento = resolveSegment(request.tipoSeguro(), data);
    Optional<BasePrice> basePriceOpt = basePriceRepository
        .findByTipoSeguroAndSegmento(request.tipoSeguro(), segmento);
    if (basePriceOpt.isEmpty()) {
      basePriceOpt = basePriceRepository.findFirstByTipoSeguroOrderByIdAsc(request.tipoSeguro());
    }
    if (basePriceOpt.isEmpty()) {
      return new PricingResponse(outcome.status, segmento, null, null, List.of(), outcome.messages, coverages);
    }

    double price = basePriceOpt.get().getPrecioBase();
    double basePrice = price;
    List<FactorDto> factors = new ArrayList<>();

    for (PricingRule rule : pricingRuleRepository.findByTipoSeguro(request.tipoSeguro())) {
      if (matchesPricingRule(rule, data)) {
        price *= rule.getFactor();
        factors.add(new FactorDto(rule.getDescripcion(), rule.getFactor()));
      }
    }

    if (!outcome.surcharges.isEmpty()) {
      for (String message : outcome.surcharges) {
        price *= SURCHARGE_FACTOR;
        factors.add(new FactorDto("Recargo: " + message, SURCHARGE_FACTOR));
      }
    }

    double finalPrice = roundMoney(price);
    return new PricingResponse(outcome.status, segmento, roundMoney(basePrice), finalPrice, factors, outcome.messages,
        coverages);
  }

  private ValidationOutcome evaluateRiskRules(String tipoSeguro, Map<String, Object> data) {
    List<String> messages = new ArrayList<>();
    List<String> surcharges = new ArrayList<>();
    QuoteStatus status = QuoteStatus.OK;

    for (RiskRule rule : riskRuleRepository.findByTipoSeguro(tipoSeguro)) {
      if (!matchesRiskRule(rule, data)) {
        continue;
      }

      String accion = normalize(rule.getAccion());
      if ("reject".equals(accion)) {
        status = QuoteStatus.REJECT;
        messages.add(rule.getMensaje());
        continue;
      }

      if ("recargo".equals(accion)) {
        if (status != QuoteStatus.REJECT) {
          status = QuoteStatus.SURCHARGE;
        }
        surcharges.add(rule.getMensaje());
        messages.add(rule.getMensaje());
      }
    }

    return new ValidationOutcome(status, messages, surcharges);
  }

  private boolean matchesRiskRule(RiskRule rule, Map<String, Object> data) {
    Object value = getValue(data, rule.getCampo());
    if (value == null) {
      return false;
    }

    String operator = normalize(rule.getOperador());
    String expected = Objects.toString(rule.getValor(), "");

    return switch (operator) {
      case ">" -> compareNumeric(value, expected, Comparison.GREATER);
      case "<" -> compareNumeric(value, expected, Comparison.LESS);
      case "=" -> compareEquals(value, expected);
      case "in" -> compareIn(value, expected);
      default -> false;
    };
  }

  private boolean matchesPricingRule(PricingRule rule, Map<String, Object> data) {
    Map<String, String> conditions = parseJsonMap(rule.getCondicion());
    if (conditions.isEmpty()) {
      return false;
    }

    for (Map.Entry<String, String> entry : conditions.entrySet()) {
      Object value = getValue(data, entry.getKey());
      if (value == null) {
        return false;
      }

      String condition = entry.getValue().trim();
      if (condition.startsWith(">")) {
        if (!compareNumeric(value, condition.substring(1), Comparison.GREATER)) {
          return false;
        }
        continue;
      }
      if (condition.startsWith("<")) {
        if (!compareNumeric(value, condition.substring(1), Comparison.LESS)) {
          return false;
        }
        continue;
      }
      if (condition.startsWith("=")) {
        condition = condition.substring(1);
      }

      if (!compareEquals(value, condition)) {
        return false;
      }
    }

    return true;
  }

  private boolean compareNumeric(Object value, String expected, Comparison comparison) {
    Double left = toDouble(value);
    Double right = toDouble(expected);
    if (left == null || right == null) {
      return false;
    }

    return switch (comparison) {
      case GREATER -> left > right;
      case LESS -> left < right;
    };
  }

  private boolean compareEquals(Object value, String expected) {
    if (value instanceof Number number) {
      Double right = toDouble(expected);
      return right != null && number.doubleValue() == right;
    }
    return normalize(Objects.toString(value, "")).equals(normalize(expected));
  }

  private boolean compareIn(Object value, String expectedJson) {
    List<String> expectedValues = parseJsonList(expectedJson);
    if (expectedValues.isEmpty()) {
      return false;
    }

    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (containsIgnoreCase(expectedValues, Objects.toString(item, ""))) {
          return true;
        }
      }
      return false;
    }

    return containsIgnoreCase(expectedValues, Objects.toString(value, ""));
  }

  private List<String> parseJsonList(String json) {
    try {
      List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
      return values != null ? values : List.of();
    } catch (Exception ex) {
      return List.of();
    }
  }

  private Map<String, String> parseJsonMap(String json) {
    try {
      Map<String, String> values = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return values != null ? values : Map.of();
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private Object getValue(Map<String, Object> data, String path) {
    if (data == null || path == null || path.isBlank()) {
      return null;
    }

    String[] parts = path.split("\\.");
    Object current = data;
    for (String part : parts) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(part);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private Map<String, Object> safeData(Map<String, Object> data) {
    if (data == null) {
      return Collections.emptyMap();
    }
    return new LinkedHashMap<>(data);
  }

  private String resolveSegment(String tipoSeguro, Map<String, Object> data) {
    Object explicit = getValue(data, "segmento");
    if (explicit != null) {
      return Objects.toString(explicit, "");
    }

    String tipo = normalize(tipoSeguro);
    if ("auto".equals(tipo)) {
      Double edad = toDouble(getValue(data, "edad"));
      if (edad != null && edad < 25) {
        return "edad_18_25";
      }
      return "edad_25_40";
    }
    if ("viaje".equals(tipo)) {
      Object destino = getValue(data, "destino");
      if (destino != null && normalize(destino.toString()).contains("usa")) {
        return "usa";
      }
      return "europa";
    }
    if ("hogar".equals(tipo)) {
      return "piso_estandar";
    }
    if ("salud".equals(tipo)) {
      return "adulto";
    }

    return "default";
  }

  private CoverageDto toCoverageDto(Coverage coverage) {
    return new CoverageDto(coverage.getNombre(), Boolean.TRUE.equals(coverage.getIncluido()),
        coverage.getPrecioExtra(), coverage.getDescripcion());
  }

  private ProductDto toProductDto(InsuranceProduct product) {
    return new ProductDto(product.getId(), product.getTipo(), product.getNombre());
  }

  private boolean containsIgnoreCase(List<String> values, String target) {
    String normalized = normalize(target);
    for (String value : values) {
      if (normalize(value).equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  private Double toDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private double roundMoney(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private enum Comparison {
    GREATER,
    LESS
  }

  private record ValidationOutcome(QuoteStatus status, List<String> messages, List<String> surcharges) {}
}
