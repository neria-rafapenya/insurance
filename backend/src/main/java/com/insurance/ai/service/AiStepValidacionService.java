package com.insurance.ai.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.ai.dto.AiHealthMember;
import com.insurance.ai.dto.AiStepRiesgoAnswers;
import com.insurance.ai.dto.AiStepTipoAnswers;
import com.insurance.ai.dto.AiStepValidacionRequest;
import com.insurance.ai.dto.AiStepValidacionResponse;
import com.insurance.ai.dto.AiValidationResult;
import com.insurance.model.Coverage;
import com.insurance.model.PricingRule;
import com.insurance.model.RiskRule;
import com.insurance.repository.CoverageRepository;
import com.insurance.repository.PricingRuleRepository;
import com.insurance.repository.RiskRuleRepository;

@Service
public class AiStepValidacionService {

  private final OpenAiClient openAiClient;
  private final RiskRuleRepository riskRuleRepository;
  private final PricingRuleRepository pricingRuleRepository;
  private final CoverageRepository coverageRepository;
  private final RiskFactorService riskFactorService;
  private final ObjectMapper objectMapper;
  private final String model;
  private final double temperature;
  private final String systemPrompt;

  public AiStepValidacionService(
      OpenAiClient openAiClient,
      RiskRuleRepository riskRuleRepository,
      PricingRuleRepository pricingRuleRepository,
      CoverageRepository coverageRepository,
      RiskFactorService riskFactorService,
      ObjectMapper objectMapper,
      @Value("${ai.model}") String model,
      @Value("${ai.temperature}") double temperature,
      @Value("${ai.validation-prompt-file:classpath:prompts/validation.md}") Resource promptResource
  ) {
    this.openAiClient = openAiClient;
    this.riskRuleRepository = riskRuleRepository;
    this.pricingRuleRepository = pricingRuleRepository;
    this.coverageRepository = coverageRepository;
    this.riskFactorService = riskFactorService;
    this.objectMapper = objectMapper;
    this.model = model;
    this.temperature = temperature;
    this.systemPrompt = loadPrompt(promptResource, defaultPrompt());
  }

  public AiStepValidacionResponse getValidation(AiStepValidacionRequest request) {
    AiStepTipoAnswers step1 = request.step1() == null ? AiStepTipoAnswers.empty() : request.step1();
    AiStepRiesgoAnswers step2 = request.step2();
    AiValidationResult result = buildValidation(step1, step2);
    String reply = buildExplanation(step1, step2, result);
    return new AiStepValidacionResponse(reply, result);
  }

  public AiStepValidacionResponse streamValidation(
      AiStepValidacionRequest request,
      java.util.function.Consumer<String> onToken
  ) {
    AiStepTipoAnswers step1 = request.step1() == null ? AiStepTipoAnswers.empty() : request.step1();
    AiStepRiesgoAnswers step2 = request.step2();
    AiValidationResult result = buildValidation(step1, step2);
    String reply = buildExplanation(step1, step2, result, onToken);
    return new AiStepValidacionResponse(reply, result);
  }

  private AiValidationResult buildValidation(AiStepTipoAnswers step1, AiStepRiesgoAnswers step2) {
    String tipoSeguro = normalize(step1.tipo());
    Map<String, Object> data = buildData(tipoSeguro, step1, step2);

    List<String> incidencias = new ArrayList<>();
    List<String> restricciones = new ArrayList<>();
    List<String> recargos = new ArrayList<>();

    List<RiskRule> riskRules = riskRuleRepository.findByTipoSeguro(tipoSeguro);
    if ("salud".equals(tipoSeguro)) {
      List<AiHealthMember> members = resolveHealthMembers(step2);
      if (!members.isEmpty()) {
        applyHealthRulesPerPerson(riskRules, members, incidencias, restricciones, recargos);
      } else {
        for (RiskRule rule : riskRules) {
          if (!matchesRiskRule(rule, data)) {
            continue;
          }
          String accion = normalize(rule.getAccion());
          if ("reject".equals(accion)) {
            incidencias.add(rule.getMensaje());
          } else if ("recargo".equals(accion)) {
            recargos.add(rule.getMensaje());
          } else if ("restriccion".equals(accion) || "restrict".equals(accion)) {
            restricciones.add(rule.getMensaje());
          }
        }
      }
    } else {
      for (RiskRule rule : riskRules) {
        if (!matchesRiskRule(rule, data)) {
          continue;
        }
        String accion = normalize(rule.getAccion());
        if ("reject".equals(accion)) {
          incidencias.add(rule.getMensaje());
        } else if ("recargo".equals(accion)) {
          recargos.add(rule.getMensaje());
        } else if ("restriccion".equals(accion) || "restrict".equals(accion)) {
          restricciones.add(rule.getMensaje());
        }
      }
    }

    for (PricingRule rule : pricingRuleRepository.findByTipoSeguro(tipoSeguro)) {
      if (!matchesPricingRule(rule, data)) {
        continue;
      }
      String label = rule.getDescripcion() + " (factor " + rule.getFactor() + ")";
      if (rule.getFactor() != null && rule.getFactor() > 1.0) {
        recargos.add(label);
      } else {
        restricciones.add(label);
      }
    }

    List<String> faltantes = buildMissingList(tipoSeguro, step1, step2);

    String estado = "ok";
    if (!incidencias.isEmpty()) {
      estado = "reject";
    } else if (!faltantes.isEmpty() || !recargos.isEmpty() || !restricciones.isEmpty()) {
      estado = "warning";
    }

    return new AiValidationResult(estado, incidencias, restricciones, recargos, faltantes);
  }

  private String buildExplanation(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult result
  ) {
    return buildExplanation(step1, step2, result, null);
  }

  private String buildExplanation(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult result,
      java.util.function.Consumer<String> onToken
  ) {
    return emphasizeData(buildDeterministicExplanation(step1, step2, result), step1, step2);
  }

  private String buildDeterministicExplanation(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult result
  ) {
    String tipo = step1.tipo() == null || step1.tipo().isBlank() ? "el seguro" : "el seguro de " + step1.tipo();
    StringBuilder sb = new StringBuilder();
    sb.append("He revisado la información proporcionada para la validación de ").append(tipo).append(".");

    sb.append("\n\n");
    if ("reject".equals(result.estado())) {
      sb.append("Resultado: rechazo. La validación ha concluido con un estado de rechazo.");
    } else if ("warning".equals(result.estado())) {
      sb.append("Resultado: advertencia. Podemos continuar, pero hay puntos a revisar.");
    } else {
      sb.append("Resultado: satisfactorio. Podemos continuar con el proceso.");
    }

    sb.append("\n\nIncidencias:");
    if (result.incidencias().isEmpty()) {
      sb.append(" No se han detectado incidencias.");
    } else {
      for (String item : result.incidencias()) {
        sb.append("\n- ").append(item);
      }
    }

    sb.append("\n\nRestricciones:");
    if (result.restricciones().isEmpty()) {
      sb.append(" No se han detectado restricciones.");
    } else {
      for (String item : result.restricciones()) {
        sb.append("\n- ").append(item);
      }
    }

    sb.append("\n\nRecargos estimados:");
    if (result.recargos().isEmpty()) {
      sb.append(" No se aplican recargos.");
    } else {
      for (String item : result.recargos()) {
        sb.append("\n- ").append(item);
      }
    }

    sb.append("\n\nFaltantes:");
    if (result.faltantes().isEmpty()) {
      sb.append(" No hay datos faltantes.");
    } else {
      for (String item : result.faltantes()) {
        sb.append("\n- ").append(item);
      }
    }

    sb.append("\n\n");
    if ("reject".equals(result.estado())) {
      sb.append("No podemos avanzar al paso de Coberturas con estos datos.");
    } else {
      sb.append("Podemos avanzar al paso de Coberturas.");
    }
    return sb.toString();
  }

  private Map<String, Object> toCoverageMap(Coverage coverage) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("nombre", coverage.getNombre());
    map.put("incluido", Boolean.TRUE.equals(coverage.getIncluido()));
    map.put("precio_extra", coverage.getPrecioExtra());
    return map;
  }

  private String emphasizeData(String text, AiStepTipoAnswers step1, AiStepRiesgoAnswers step2) {
    if (text == null || text.isBlank()) {
      return text;
    }
    List<String> values = new ArrayList<>();
    addValueParts(values, step1.tipo());
    addValueParts(values, step1.subtipo());
    addValueParts(values, step1.uso());
    addValueParts(values, step1.ubicacion());
    addValueParts(values, step1.destino());

    if (step2 != null) {
      addValueParts(values, step2.autoVehicle());
      addValueParts(values, step2.autoAge() == null ? null : String.valueOf(step2.autoAge()));
      addValueParts(values, step2.autoUsage());
      addValueParts(values, step2.autoSpecs());
      addValueParts(values, step2.autoMileageParking());
      addValueParts(values, step2.homeOwnership());
      addValueParts(values, step2.homeUsage());
      addValueParts(values, step2.homeTypeDetails());
      addValueParts(values, step2.homeLocationContent());
      addValueParts(values, step2.healthAge() == null ? null : String.valueOf(step2.healthAge()));
      addValueParts(values, step2.healthSmoker());
      addValueParts(values, step2.healthPathologies());
      addValueParts(values, step2.healthPlan());
      addValueParts(values, step2.healthFamilyDetails());
      for (AiHealthMember member : resolveHealthMembers(step2)) {
        addValueParts(values, member.edad() == null ? null : String.valueOf(member.edad()));
        addValueParts(values, member.fumador());
        addValueParts(values, member.patologias());
      }
      addValueParts(values, step2.travelScope());
      addValueParts(values, step2.travelDestination());
      addValueParts(values, step2.travelPeopleCount() == null ? null : String.valueOf(step2.travelPeopleCount()));
      addValueParts(values, step2.travelPeopleAges());
      addValueParts(values, step2.travelDurationDays() == null ? null : String.valueOf(step2.travelDurationDays()));
      addValueParts(values, step2.travelPurpose());
    }

    if (values.isEmpty()) {
      return emphasizeSections(text);
    }

    String result = text;
    values.sort((a, b) -> Integer.compare(b.length(), a.length()));
    for (String value : values) {
      String escaped = java.util.regex.Pattern.quote(value);
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?<!\\w)" + escaped + "(?!\\w)");
      java.util.regex.Matcher matcher = pattern.matcher(result);
      StringBuffer sb = new StringBuffer();
      while (matcher.find()) {
        String match = matcher.group();
        if (match.startsWith("**") && match.endsWith("**")) {
          matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(match));
        } else {
          matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("**" + match + "**"));
        }
      }
      matcher.appendTail(sb);
      result = sb.toString();
    }

    return emphasizeSections(result);
  }

  private void addValueParts(List<String> values, String value) {
    if (value == null) {
      return;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    addCandidate(values, trimmed);

    for (String chunk : trimmed.split("[,;\\n/()]+")) {
      String part = chunk.trim();
      if (part.isEmpty()) {
        continue;
      }
      addCandidate(values, part);
      String[] connectors = part.split("\\s+(?:y|o|u|and|or)\\s+");
      if (connectors.length > 1) {
        for (String token : connectors) {
          addCandidate(values, token.trim());
        }
      }
    }

    java.util.regex.Matcher numberWithUnit = java.util.regex.Pattern.compile(
        "\\b\\d{1,3}(?:[\\.,]\\d{3})*(?:[\\.,]\\d+)?\\s*(?:m2|m²|km|anos|años|euros|€)\\b",
        java.util.regex.Pattern.CASE_INSENSITIVE
    ).matcher(trimmed);
    while (numberWithUnit.find()) {
      addCandidate(values, numberWithUnit.group());
    }

    java.util.regex.Matcher numbers = java.util.regex.Pattern.compile(
        "\\b\\d{1,3}(?:[\\.,]\\d{3})*(?:[\\.,]\\d+)?\\b"
    ).matcher(trimmed);
    while (numbers.find()) {
      addCandidate(values, numbers.group());
    }
  }

  private void addCandidate(List<String> values, String value) {
    if (value == null) {
      return;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    String normalized = normalize(trimmed);
    if (normalized.equals("si") || normalized.equals("no")) {
      return;
    }
    if (normalized.length() < 3 && !trimmed.matches(".*\\d.*")) {
      return;
    }
    for (String existing : values) {
      if (normalize(existing).equals(normalized)) {
        return;
      }
    }
    values.add(trimmed);
  }

  private String emphasizeSections(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String output = text;
    output = output.replaceAll("(?i)(?<!\\*)Resultado:", "**Resultado:**");
    output = output.replaceAll("(?i)(?<!\\*)Recargos estimados:", "**Recargos estimados:**");

    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "(?m)^(\\s*)(Podemos[^\\n]*Coberturas\\.?)(\\s*)$",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );
    java.util.regex.Matcher matcher = pattern.matcher(output);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String leading = matcher.group(1);
      String sentence = matcher.group(2);
      String trailing = matcher.group(3);
      String replacement = sentence.startsWith("**") && sentence.endsWith("**")
          ? sentence
          : "**" + sentence + "**";
      matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(leading + replacement + trailing));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  private List<String> buildMissingList(
      String tipoSeguro,
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2
  ) {
    List<String> missing = new ArrayList<>();
    requireField(step1.tipo(), "Tipo de seguro", missing);
    requireField(step1.subtipo(), "Nivel de protección", missing);

    if ("auto".equals(tipoSeguro)) {
      requireField(step2 == null ? null : step2.autoVehicle(), "Vehículo a asegurar", missing);
      requireField(step2 == null ? null : step2.autoAge(), "Edad", missing);
      requireField(step2 == null ? null : coalesce(step2.autoUsage(), step1.uso()), "Uso del vehículo", missing);
      requireField(step2 == null ? null : step2.autoSpecs(), "Marca, modelo, año y potencia", missing);
      requireField(step2 == null ? null : step2.autoMileageParking(), "Kilometraje y parking", missing);
    }

    if ("hogar".equals(tipoSeguro)) {
      requireField(step2 == null ? null : coalesce(step2.homeOwnership(), step1.subtipo()), "Tenencia de la vivienda", missing);
      requireField(step2 == null ? null : coalesce(step2.homeUsage(), step1.uso()), "Uso de la vivienda", missing);
      requireField(step2 == null ? null : step2.homeTypeDetails(), "Tipo de vivienda, m2 y año", missing);
      requireField(step2 == null ? null : step2.homeLocationContent(), "Ubicación y contenido", missing);
    }

    if ("salud".equals(tipoSeguro)) {
      requireField(step2 == null ? null : step2.healthAge(), "Edad", missing);
      requireField(step2 == null ? null : step2.healthSmoker(), "Fumador", missing);
      requireField(step2 == null ? null : step2.healthPathologies(), "Patologías", missing);
      requireField(step2 == null ? null : step2.healthPlan(), "Modalidad", missing);
      if (step2 != null && normalize(step2.healthPlan()).contains("familiar")) {
        List<AiHealthMember> members = resolveHealthMembers(step2, false);
        if (members.isEmpty()) {
          missing.add("Personas aseguradas");
        } else {
          int index = 1;
          for (AiHealthMember member : members) {
            if (member.edad() == null) {
              missing.add("Edad persona " + index);
            }
            if (member.fumador() == null || member.fumador().isBlank()) {
              missing.add("Fumador persona " + index);
            }
            if (member.patologias() == null || member.patologias().isBlank()) {
              missing.add("Patologías persona " + index);
            }
            index += 1;
          }
        }
      }
    }

    if ("viaje".equals(tipoSeguro)) {
      requireField(step2 == null ? null : step2.travelScope(), "Tipo de viaje", missing);
      requireField(step2 == null ? null : step2.travelDestination(), "Destino", missing);
      requireField(step2 == null ? null : step2.travelPeopleCount(), "Número de personas", missing);
      requireField(step2 == null ? null : step2.travelPeopleAges(), "Edades", missing);
      requireField(step2 == null ? null : step2.travelDurationDays(), "Duración", missing);
      requireField(step2 == null ? null : step2.travelPurpose(), "Motivo del viaje", missing);
    }

    return missing;
  }

  private void requireField(Object value, String label, List<String> missing) {
    if (value == null) {
      missing.add(label);
      return;
    }
    if (value instanceof String text && text.trim().isEmpty()) {
      missing.add(label);
    }
  }

  private Map<String, Object> buildData(
      String tipoSeguro,
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2
  ) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tipo_seguro", tipoSeguro);
    data.put("subtipo", normalize(step1.subtipo()));
    data.put("uso", normalize(step1.uso()));

    if (step2 == null) {
      return data;
    }

    Integer edad = null;
    Integer edadMin = null;

    if ("auto".equals(tipoSeguro)) {
      edad = step2.autoAge();
      edadMin = edad;
    } else if ("salud".equals(tipoSeguro)) {
      List<AiHealthMember> members = resolveHealthMembers(step2);
      edad = resolveHealthMaxAge(step2, members);
      edadMin = members.stream()
          .map(AiHealthMember::edad)
          .filter(Objects::nonNull)
          .min(Integer::compareTo)
          .orElse(step2.healthAge());

      String aggregatedPathologies = members.stream()
          .map(AiHealthMember::patologias)
          .filter(Objects::nonNull)
          .filter(value -> !value.isBlank())
          .reduce((left, right) -> left + " " + right)
          .orElse(step2.healthPathologies());
      String aggregatedSmoker = members.stream()
          .map(AiHealthMember::fumador)
          .filter(Objects::nonNull)
          .filter(value -> !value.isBlank())
          .reduce((left, right) -> left + " " + right)
          .orElse(step2.healthSmoker());

      if (aggregatedPathologies != null) {
        data.put("healthPathologies", aggregatedPathologies);
      }
      if (aggregatedSmoker != null) {
        data.put("healthSmoker", aggregatedSmoker);
      }
    } else if ("viaje".equals(tipoSeguro)) {
      edad = extractMinAge(step2.travelPeopleAges());
      edadMin = edad;
    }

    if (edad != null) {
      data.put("edad", edad);
    }
    if (edadMin != null) {
      data.put("edad_min", edadMin);
    }

    if (step2.travelDurationDays() != null) {
      data.put("duracion_dias", step2.travelDurationDays());
    }

    if (step2.homeLocationContent() != null) {
      data.put("homeLocationContent", step2.homeLocationContent());
    }

    if (step2.travelDestination() != null) {
      data.put("travelDestination", step2.travelDestination());
    }

    if (step2.healthPathologies() != null && !data.containsKey("healthPathologies")) {
      data.put("healthPathologies", step2.healthPathologies());
    }

    if (step2.healthSmoker() != null && !data.containsKey("healthSmoker")) {
      data.put("healthSmoker", step2.healthSmoker());
    }

    return riskFactorService.applyFactors(tipoSeguro, data);
  }

  private void applyHealthRulesPerPerson(
      List<RiskRule> rules,
      List<AiHealthMember> members,
      List<String> incidencias,
      List<String> restricciones,
      List<String> recargos
  ) {
    int index = 1;
    for (AiHealthMember member : members) {
      Map<String, Object> personData = new LinkedHashMap<>();
      if (member.edad() != null) {
        personData.put("edad", member.edad());
        personData.put("edad_min", member.edad());
      }
      if (member.patologias() != null) {
        personData.put("healthPathologies", member.patologias());
      }
      if (member.fumador() != null) {
        personData.put("healthSmoker", member.fumador());
      }
      personData = riskFactorService.applyFactors("salud", personData);
      String prefix = "Persona " + index + (member.edad() != null ? " (" + member.edad() + " años)" : "") + ": ";
      for (RiskRule rule : rules) {
        if (!matchesRiskRule(rule, personData)) {
          continue;
        }
        String accion = normalize(rule.getAccion());
        String message = prefix + rule.getMensaje();
        if ("reject".equals(accion)) {
          incidencias.add(message);
        } else if ("recargo".equals(accion)) {
          recargos.add(message);
        } else if ("restriccion".equals(accion) || "restrict".equals(accion)) {
          restricciones.add(message);
        }
      }
      index += 1;
    }
  }

  private Integer extractMinAge(String agesText) {
    if (agesText == null || agesText.isBlank()) {
      return null;
    }
    List<Integer> ages = new ArrayList<>();
    for (String part : agesText.split("[^0-9]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        ages.add(Integer.parseInt(part));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return ages.stream().min(Integer::compareTo).orElse(null);
  }

  private Integer extractMaxAge(String agesText) {
    if (agesText == null || agesText.isBlank()) {
      return null;
    }
    List<Integer> ages = new ArrayList<>();
    for (String part : agesText.split("[^0-9]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        ages.add(Integer.parseInt(part));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return ages.stream().max(Integer::compareTo).orElse(null);
  }

  private Integer resolveHealthMaxAge(AiStepRiesgoAnswers step2) {
    List<AiHealthMember> members = resolveHealthMembers(step2);
    return resolveHealthMaxAge(step2, members);
  }

  private Integer resolveHealthMaxAge(AiStepRiesgoAnswers step2, List<AiHealthMember> members) {
    Integer primary = step2 == null ? null : step2.healthAge();
    Integer familyMax = members.stream()
        .map(AiHealthMember::edad)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);
    if (familyMax != null) {
      return familyMax;
    }
    Integer detailsMax = step2 == null ? null : extractMaxAge(step2.healthFamilyDetails());
    if (detailsMax != null) {
      return detailsMax;
    }
    return primary;
  }

  private List<AiHealthMember> resolveHealthMembers(AiStepRiesgoAnswers step2) {
    return resolveHealthMembers(step2, true);
  }

  private List<AiHealthMember> resolveHealthMembers(AiStepRiesgoAnswers step2, boolean includePrimaryFallback) {
    if (step2 == null) {
      return List.of();
    }
    if (step2.healthFamilyMembers() != null && !step2.healthFamilyMembers().isEmpty()) {
      return step2.healthFamilyMembers();
    }
    List<AiHealthMember> members = new ArrayList<>();
    if (step2.healthFamilyDetails() != null && !step2.healthFamilyDetails().isBlank()) {
      List<Integer> ages = new ArrayList<>();
      for (String part : step2.healthFamilyDetails().split("[^0-9]+")) {
        if (part.isBlank()) {
          continue;
        }
        try {
          ages.add(Integer.parseInt(part));
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
      for (Integer age : ages) {
        members.add(new AiHealthMember(null, age, null, null));
      }
    }
    if (includePrimaryFallback && members.isEmpty() && step2.healthAge() != null) {
      members.add(new AiHealthMember(null, step2.healthAge(), step2.healthSmoker(), step2.healthPathologies()));
    }
    return members;
  }


  private boolean matchesRiskRule(RiskRule rule, Map<String, Object> data) {
    Object value = data.get(normalize(rule.getCampo()));
    if (value == null) {
      value = data.get(rule.getCampo());
    }
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
      Object value = data.get(normalize(entry.getKey()));
      if (value == null) {
        value = data.get(entry.getKey());
      }
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

  private Map<String, String> parseJsonMap(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<Map<String, String>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
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
    String normalized = normalize(value.toString());
    String normalizedExpected = normalize(expected);
    return normalized.equals(normalizedExpected);
  }

  private boolean compareIn(Object value, String expected) {
    String normalized = normalize(value.toString());
    for (String entry : expected.split(",")) {
      if (normalized.equals(normalize(entry.trim()))) {
        return true;
      }
    }
    return false;
  }

  private Double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.valueOf(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String coalesce(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second;
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
    GREATER,
    LESS
  }

  private String fallbackExplanation(AiValidationResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("He revisado los datos disponibles y este es el resultado.\\n");
    sb.append("Estado: ").append(result.estado()).append(".\\n");
    if (!result.incidencias().isEmpty()) {
      sb.append("Incidencias: ").append(String.join(", ", result.incidencias())).append(".\\n");
    }
    if (!result.restricciones().isEmpty()) {
      sb.append("Restricciones: ").append(String.join(", ", result.restricciones())).append(".\\n");
    }
    if (!result.recargos().isEmpty()) {
      sb.append("Recargos: ").append(String.join(", ", result.recargos())).append(".\\n");
    }
    if (!result.faltantes().isEmpty()) {
      sb.append("Faltantes: ").append(String.join(", ", result.faltantes())).append(".\\n");
    }
    return sb.toString();
  }

  private String loadPrompt(Resource resource, String fallback) {
    try (InputStream is = resource.getInputStream()) {
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return fallback;
    }
  }

  private String defaultPrompt() {
    return """
      Eres un asistente experto en validación de riesgos.
      Recibirás un JSON con datos de la solicitud, reglas y un resultado estructurado.
      Devuelve una explicación en español, clara y detallada, en Markdown.
      Explica incidencias, restricciones, recargos y faltantes, y menciona que el siguiente paso son Coberturas.
      No devuelvas JSON, solo texto.
      """;
  }
}
