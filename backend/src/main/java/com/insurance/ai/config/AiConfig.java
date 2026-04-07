package com.insurance.ai.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiConfig {
  private final Map<String, List<String>> synonyms;
  private final List<String> boldKeywords;

  public AiConfig(
      @Value("classpath:ai-config.json") Resource resource,
      ObjectMapper objectMapper
  ) {
    AiConfigPayload payload = load(resource, objectMapper);
    this.synonyms = payload.synonyms == null ? Map.of() : payload.synonyms;
    this.boldKeywords = payload.boldKeywords == null ? List.of() : payload.boldKeywords;
  }

  public Map<String, List<String>> getSynonyms() {
    return synonyms;
  }

  public List<String> getBoldKeywords() {
    return boldKeywords;
  }

  private AiConfigPayload load(Resource resource, ObjectMapper objectMapper) {
    if (resource == null) {
      return new AiConfigPayload();
    }
    try (InputStream input = resource.getInputStream()) {
      String json = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
      if (json.isBlank()) {
        return new AiConfigPayload();
      }
      return objectMapper.readValue(json, AiConfigPayload.class);
    } catch (Exception ex) {
      return new AiConfigPayload();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class AiConfigPayload {
    public Map<String, List<String>> synonyms;
    public List<String> boldKeywords;
  }
}
