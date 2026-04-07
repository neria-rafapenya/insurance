package com.insurance.ai.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.model.RiskFactor;
import com.insurance.repository.RiskFactorRepository;

@Service
public class RiskFactorService {
  private final RiskFactorRepository riskFactorRepository;
  private final ObjectMapper objectMapper;

  public RiskFactorService(
      RiskFactorRepository riskFactorRepository,
      ObjectMapper objectMapper
  ) {
    this.riskFactorRepository = riskFactorRepository;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> applyFactors(String tipoSeguro, Map<String, Object> data) {
    Map<String, Object> result = new LinkedHashMap<>(data);
    List<RiskFactor> factors =
        riskFactorRepository.findByTipoSeguroOrTipoSeguroIsNullOrderByPrioridadDesc(tipoSeguro);
    for (RiskFactor factor : factors) {
      if (factor == null) {
        continue;
      }
      if (Boolean.FALSE.equals(factor.getActivo())) {
        continue;
      }
      String sourceKey = factor.getFuente();
      Object source = result.get(sourceKey);
      if (source == null) {
        continue;
      }
      if (!matchesFactor(factor, source)) {
        continue;
      }
      Object value = parseResultValue(factor.getValorResultado());
      String target = factor.getCampo();
      Object existing = result.get(target);
      if (existing instanceof Boolean existingBool && Boolean.TRUE.equals(existingBool)) {
        continue;
      }
      if (existing != null && !(existing instanceof Boolean)) {
        continue;
      }
      if (existing instanceof Boolean existingFalse
          && Boolean.FALSE.equals(existingFalse)
          && value instanceof Boolean newBool
          && Boolean.TRUE.equals(newBool)) {
        result.put(target, value);
        continue;
      }
      if (existing == null) {
        result.put(target, value);
      }
    }
    return result;
  }

  private boolean matchesFactor(RiskFactor factor, Object source) {
    String type = normalize(factor.getTipoMatch());
    String rawMatch = factor.getValorMatch() == null ? "" : factor.getValorMatch().trim();
    List<String> matchValues = parseList(rawMatch);

    switch (type) {
      case "keyword_any":
      case "keyword":
      case "contains_any":
        return matchKeywordAny(source, matchValues);
      case "regex":
        return matchRegex(source, rawMatch);
      case "postal_prefix":
        return matchPostalPrefix(source, matchValues);
      case "equals":
      case "equal":
        return matchEquals(source, matchValues);
      case "lt":
      case "less":
        return matchNumeric(source, rawMatch, Comparison.LESS);
      case "gt":
      case "greater":
        return matchNumeric(source, rawMatch, Comparison.GREATER);
      default:
        return false;
    }
  }

  private boolean matchKeywordAny(Object source, List<String> keywords) {
    String text = normalize(Objects.toString(source, ""));
    if (text.isBlank()) {
      return false;
    }
    for (String keyword : keywords) {
      String normalized = normalize(keyword);
      if (!normalized.isBlank() && text.contains(normalized)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchRegex(Object source, String pattern) {
    String text = Objects.toString(source, "");
    if (text.isBlank() || pattern.isBlank()) {
      return false;
    }
    try {
      return java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
          .matcher(text)
          .find();
    } catch (Exception ex) {
      return false;
    }
  }

  private boolean matchPostalPrefix(Object source, List<String> prefixes) {
    String text = normalize(Objects.toString(source, ""));
    if (text.isBlank()) {
      return false;
    }
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("\\b(\\d{5})\\b").matcher(text);
    if (!matcher.find()) {
      return false;
    }
    String postal = matcher.group(1);
    if (postal.length() < 2) {
      return false;
    }
    String prefix = postal.substring(0, 2);
    for (String candidate : prefixes) {
      if (prefix.equals(candidate.trim())) {
        return true;
      }
    }
    return false;
  }

  private boolean matchEquals(Object source, List<String> values) {
    if (source == null) {
      return false;
    }
    String normalized = normalize(Objects.toString(source, ""));
    for (String value : values) {
      if (normalized.equals(normalize(value))) {
        return true;
      }
    }
    return false;
  }

  private boolean matchNumeric(Object source, String expected, Comparison comparison) {
    Double left = toDouble(source);
    Double right = toDouble(expected);
    if (left == null || right == null) {
      return false;
    }
    return comparison == Comparison.LESS ? left < right : left > right;
  }

  private Object parseResultValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return Boolean.TRUE;
    }
    String normalized = normalize(raw);
    if ("true".equals(normalized) || "si".equals(normalized) || "sí".equals(normalized)) {
      return Boolean.TRUE;
    }
    if ("false".equals(normalized) || "no".equals(normalized)) {
      return Boolean.FALSE;
    }
    Double numeric = toDouble(raw);
    if (numeric != null) {
      return numeric;
    }
    return raw;
  }

  private List<String> parseList(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith("[")) {
      try {
        return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
      } catch (Exception ignored) {
        // fall back
      }
    }
    String[] parts = trimmed.split("\\s*,\\s*");
    List<String> values = new ArrayList<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        values.add(part.trim());
      }
    }
    return values;
  }

  private Double toDouble(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    String text = Objects.toString(value, "").trim();
    if (text.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .trim();
  }

  private enum Comparison {
    LESS,
    GREATER
  }
}
