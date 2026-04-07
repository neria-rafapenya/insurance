package com.insurance.ai.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.ai.dto.AiStepTipoAnswers;
import com.insurance.ai.dto.AiStepTipoRequest;
import com.insurance.ai.dto.AiStepTipoResponse;
import com.insurance.catalog.dto.CatalogOptionsResponse;
import com.insurance.catalog.service.CatalogService;

@Service
public class AiStepTipoService {
  private enum ExpectedField {
    TIPO,
    SUBTIPO,
    USO,
    UBICACION,
    DESTINO,
    NONE
  }
  private final OpenAiClient openAiClient;
  private final CatalogService catalogService;
  private final ObjectMapper objectMapper;
  private final String model;
  private final double temperature;
  private final String outputFormat;
  private final String systemPrompt;
  private final PromptTemplateService promptTemplateService;

  private final List<String> boldKeywords;
  private final Map<String, List<String>> synonymsMap;

  public AiStepTipoService(
      OpenAiClient openAiClient,
      CatalogService catalogService,
      ObjectMapper objectMapper,
      PromptTemplateService promptTemplateService,
      @Value("${ai.model}") String model,
      @Value("${ai.temperature}") double temperature,
      @Value("${ai.output-format}") String outputFormat,
      @Value("${ai.prompt-file}") Resource promptResource,
      @Value("classpath:ai-config.json") Resource configResource
  ) {
    this.openAiClient = openAiClient;
    this.catalogService = catalogService;
    this.objectMapper = objectMapper;
    this.promptTemplateService = promptTemplateService;
    this.model = model;
    this.temperature = temperature;
    this.outputFormat = outputFormat;
    this.systemPrompt = loadPrompt(promptResource, defaultPrompt());

    AiConfig config = loadConfig(configResource);
    this.boldKeywords = config.boldKeywords();
    this.synonymsMap = config.synonyms();
  }

  private record AiConfig(List<String> boldKeywords, Map<String, List<String>> synonyms) {}

  private AiConfig loadConfig(Resource resource) {
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readValue(is, AiConfig.class);
    } catch (Exception e) {
      return new AiConfig(List.of(), Map.of());
    }
  }

  public AiStepTipoResponse getNext(AiStepTipoRequest request) {
    CatalogOptionsResponse options = catalogService.getOptions();
    AiStepTipoAnswers answers = request.answers() == null ? AiStepTipoAnswers.empty() : request.answers();
    String language = detectLanguage(request.input());
    boolean capabilities = isCapabilitiesQuestion(request.input());
    ExpectedField expected = getExpectedField(answers, options);
    boolean invalidInput = !capabilities
        && hasInvalidOptionInput(request.input(), expected, answers, options);
    AiStepTipoAnswers normalizedAnswers = applyDeterministicUpdate(answers, request.input(), options);

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("mode", "json");
    context.put("language", language);
    context.put("intent", capabilities ? "capabilities" : "normal");
    context.put("invalid_input", invalidInput);
    context.put("input", request.input());
    context.put("answers", normalizedAnswers);
    Map<String, Object> optionsMap = new LinkedHashMap<>();
    optionsMap.put("subtypes", options.subtypes());
    optionsMap.put("usages", options.usages());
    context.put("options", optionsMap);

    String userPayload;
    try {
      userPayload = objectMapper.writeValueAsString(context);
    } catch (Exception ex) {
      throw new IllegalStateException("No se pudo serializar el contexto", ex);
    }

    List<OpenAiClient.Message> messages = List.of(
        new OpenAiClient.Message("system", systemPrompt),
        new OpenAiClient.Message("user", userPayload)
    );

    String content = openAiClient.chat(model, messages, temperature, outputFormat);

    try {
      AiStepTipoResponse response = objectMapper.readValue(content, AiStepTipoResponse.class);
      AiStepTipoAnswers merged = mergeAnswers(normalizedAnswers, response.answers());
      boolean done = response.done() || isComplete(merged, options);
      String reply = response.reply();
      if (!done && expected == ExpectedField.SUBTIPO && "es".equals(language)) {
        String forced = buildNextQuestion(merged, options, language);
        if (!isBlank(forced)) {
          reply = forced;
        }
      }
      if (done) {
        reply = buildFinalSummary(merged, language, options);
      } else if (isBlank(reply)) {
        reply = buildFallbackReply(
            done,
            invalidInput,
            merged,
            options,
            language
        );
      }
      return new AiStepTipoResponse(reply, merged, done, language);
    } catch (Exception ex) {
      return new AiStepTipoResponse(
          buildErrorReply(language),
          normalizedAnswers,
          false,
          language
      );
    }
  }

  public AiStepTipoResponse streamNext(AiStepTipoRequest request, java.util.function.Consumer<String> onToken) {
    CatalogOptionsResponse options = catalogService.getOptions();
    AiStepTipoAnswers answers = request.answers() == null ? AiStepTipoAnswers.empty() : request.answers();
    String language = detectLanguage(request.input());
    boolean capabilities = isCapabilitiesQuestion(request.input());
    ExpectedField expected = getExpectedField(answers, options);
    boolean invalidInput = !capabilities
        && hasInvalidOptionInput(request.input(), expected, answers, options);
    AiStepTipoAnswers normalizedAnswers = applyDeterministicUpdate(answers, request.input(), options);

    boolean done = isComplete(normalizedAnswers, options);
    String nextQuestion = buildNextQuestion(normalizedAnswers, options, language);

    if (!done && expected == ExpectedField.SUBTIPO && "es".equals(language) && !capabilities && !invalidInput) {
      if (nextQuestion != null && !nextQuestion.isBlank()) {
        if (onToken != null) {
          streamText(nextQuestion, onToken);
        }
        return new AiStepTipoResponse(nextQuestion, normalizedAnswers, false, language);
      }
    }

    Map<String, Object> context = new LinkedHashMap<>();
    context.put("mode", "reply_only");
    context.put("language", language);
    context.put("intent", capabilities ? "capabilities" : "normal");
    context.put("invalid_input", invalidInput);
    context.put("input", request.input());
    context.put("answers", normalizedAnswers);
    context.put("done", done);
    context.put("next_question", nextQuestion);
    Map<String, Object> optionsMap = new LinkedHashMap<>();
    optionsMap.put("subtypes", options.subtypes());
    optionsMap.put("usages", options.usages());
    context.put("options", optionsMap);

    String userPayload;
    try {
      userPayload = objectMapper.writeValueAsString(context);
    } catch (Exception ex) {
      throw new IllegalStateException("No se pudo serializar el contexto", ex);
    }

    List<OpenAiClient.Message> messages = List.of(
        new OpenAiClient.Message("system", systemPrompt),
        new OpenAiClient.Message("user", userPayload)
    );

    StringBuilder streamed = new StringBuilder();
    boolean[] decided = {false};
    boolean[] allowStreaming = {true};
    java.util.function.Consumer<String> safeToken = token -> {
      streamed.append(token);
      if (!decided[0]) {
        String trimmed = streamed.toString().stripLeading();
        if (!trimmed.isEmpty()) {
          decided[0] = true;
          if (trimmed.startsWith("{")) {
            allowStreaming[0] = false;
          }
        }
      }
      if (allowStreaming[0] && onToken != null) {
        onToken.accept(token);
      }
    };

    String reply = openAiClient.streamChat(model, messages, temperature, safeToken);
    AiStepTipoResponse parsed = tryParseResponse(reply);
    if (parsed != null) {
      normalizedAnswers = mergeAnswers(normalizedAnswers, parsed.answers());
      done = parsed.done() || isComplete(normalizedAnswers, options);
      reply = parsed.reply();
      if (done) {
        reply = buildFinalSummary(normalizedAnswers, language, options);
      }
      if (!allowStreaming[0] && onToken != null && reply != null && !reply.isBlank()) {
        streamText(reply, onToken);
      }
    } else if (!allowStreaming[0]) {
      reply = "";
    }
    if (reply == null || reply.isBlank()) {
      reply = buildFallbackReply(
          done,
          invalidInput,
          normalizedAnswers,
          options,
          language
      );
      if (onToken != null) {
        streamText(reply, onToken);
      }
    }

    return new AiStepTipoResponse(reply, normalizedAnswers, done, language);
  }

  private String loadPrompt(Resource resource, String fallback) {
    if (resource == null) {
      return fallback;
    }
    try (InputStream input = resource.getInputStream()) {
      String prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
      return prompt.isBlank() ? fallback : prompt;
    } catch (Exception ex) {
      return fallback;
    }
  }

  private String defaultPrompt() {
    return "Eres un asistente que guía un wizard de seguros. "
        + "Debes preguntar el siguiente dato necesario y actualizar respuestas. "
        + "Usa únicamente las opciones proporcionadas. "
        + "Devuelve SOLO JSON válido con las claves: reply (string), answers (object), done (boolean). "
        + "answers debe incluir: tipo, subtipo, uso, ubicacion, destino.";
  }

  private AiStepTipoAnswers applyDeterministicUpdate(
      AiStepTipoAnswers current,
      String input,
      CatalogOptionsResponse options
  ) {
    if (input == null || input.isBlank()) {
      return current;
    }

    String tipo = current.tipo();
    String subtipo = current.subtipo();
    String uso = current.uso();
    String ubicacion = current.ubicacion();
    String destino = current.destino();

    boolean matched = false;

    if (isBlank(tipo)) {
      String matchedTipo = matchTipo(input);
      if (matchedTipo != null) {
        tipo = matchedTipo;
        matched = true;
      }
    }

    if (!isBlank(tipo) && isBlank(subtipo)) {
      String matchedSubtipo = matchOption(input, options.subtypes().get(tipo));
      if (matchedSubtipo != null) {
        subtipo = matchedSubtipo;
        matched = true;
      }
    }

    if (!isBlank(tipo) && isBlank(uso)) {
      String matchedUso = matchOption(input, options.usages().get(tipo));
      if (matchedUso != null) {
        uso = matchedUso;
        matched = true;
      }
    }

    if (!matched && !isBlank(tipo)) {
      String trimmed = input.trim();
      if ("viaje".equalsIgnoreCase(tipo) && isBlank(destino)) {
        destino = trimmed;
      } else if (!"viaje".equalsIgnoreCase(tipo) && isBlank(ubicacion)) {
        ubicacion = trimmed;
      }
    }

    return new AiStepTipoAnswers(tipo, subtipo, uso, ubicacion, destino);
  }

  private AiStepTipoAnswers mergeAnswers(AiStepTipoAnswers base, AiStepTipoAnswers incoming) {
    if (incoming == null) {
      return base;
    }
    String tipo = coalesce(incoming.tipo(), base.tipo());
    String subtipo = coalesce(incoming.subtipo(), base.subtipo());
    String uso = coalesce(incoming.uso(), base.uso());
    String ubicacion = coalesce(incoming.ubicacion(), base.ubicacion());
    String destino = coalesce(incoming.destino(), base.destino());
    return new AiStepTipoAnswers(tipo, subtipo, uso, ubicacion, destino);
  }

  private boolean isComplete(AiStepTipoAnswers answers, CatalogOptionsResponse options) {
    if (isBlank(answers.tipo()) || isBlank(answers.subtipo())) {
      return false;
    }

    boolean requiresUso = requiresUso(answers, options);

    if (requiresUso && isBlank(answers.uso())) {
      return false;
    }

    if ("viaje".equalsIgnoreCase(answers.tipo())) {
      return !isBlank(answers.destino());
    }

    return !isBlank(answers.ubicacion());
  }

  private ExpectedField getExpectedField(AiStepTipoAnswers answers, CatalogOptionsResponse options) {
    if (isBlank(answers.tipo())) {
      return ExpectedField.TIPO;
    }
    if (isBlank(answers.subtipo())) {
      return ExpectedField.SUBTIPO;
    }
    if (requiresUso(answers, options) && isBlank(answers.uso())) {
      return ExpectedField.USO;
    }
    if ("viaje".equalsIgnoreCase(answers.tipo())) {
      return isBlank(answers.destino()) ? ExpectedField.DESTINO : ExpectedField.NONE;
    }
    return isBlank(answers.ubicacion()) ? ExpectedField.UBICACION : ExpectedField.NONE;
  }

  private boolean requiresUso(AiStepTipoAnswers answers, CatalogOptionsResponse options) {
    return options.usages() != null
        && options.usages().containsKey(answers.tipo())
        && options.usages().get(answers.tipo()) != null
        && !options.usages().get(answers.tipo()).isEmpty();
  }

  private boolean hasInvalidOptionInput(
      String input,
      ExpectedField expected,
      AiStepTipoAnswers answers,
      CatalogOptionsResponse options
  ) {
    if (input == null || input.isBlank()) {
      return false;
    }
    if (expected == ExpectedField.TIPO) {
      return matchTipo(input) == null;
    }
    if (expected == ExpectedField.SUBTIPO) {
      List<String> subtypes = options.subtypes() == null ? null : options.subtypes().get(answers.tipo());
      if (subtypes == null || subtypes.isEmpty()) {
        return false;
      }
      return matchOption(input, subtypes) == null;
    }
    if (expected == ExpectedField.USO) {
      List<String> usos = options.usages() == null ? null : options.usages().get(answers.tipo());
      if (usos == null || usos.isEmpty()) {
        return false;
      }
      return matchOption(input, usos) == null;
    }
    return false;
  }

  private String buildRetryQuestion(
      AiStepTipoAnswers answers,
      CatalogOptionsResponse options,
      String language
  ) {
    String question = buildNextQuestion(answers, options, language);
    if (question == null || question.isBlank()) {
      return "No he entendido tu respuesta. ¿Puedes repetirlo?";
    }
    return "No he entendido tu respuesta. " + question;
  }

  private String buildFallbackReply(
      boolean done,
      boolean invalidInput,
      AiStepTipoAnswers answers,
      CatalogOptionsResponse options,
      String language
  ) {
    if (done) {
      return buildFinalSummary(answers, language, options);
    }
    if (invalidInput) {
      return buildRetryQuestion(answers, options, language);
    }
    String question = buildNextQuestion(answers, options, language);
    return question == null ? buildErrorReply(language) : question;
  }

  private String buildErrorReply(String language) {
    return switch (language) {
      case "en" -> "I couldn't process your answer. Could you repeat it?";
      case "pt" -> "Não consegui processar sua resposta. Você pode repetir?";
      case "fr" -> "Je n'ai pas pu traiter votre réponse. Pouvez-vous répéter ?";
      case "it" -> "Non sono riuscito a elaborare la tua risposta. Puoi ripeterla?";
      default -> "No he podido procesar tu respuesta. ¿Puedes repetirlo?";
    };
  }

  private AiStepTipoResponse tryParseResponse(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String trimmed = text.trim();
    if (!trimmed.startsWith("{")) {
      return null;
    }
    try {
      return objectMapper.readValue(trimmed, AiStepTipoResponse.class);
    } catch (Exception ex) {
      return null;
    }
  }

  private String buildNextQuestion(
      AiStepTipoAnswers answers,
      CatalogOptionsResponse options,
      String language
  ) {
    String tipo = coalesce(answers.tipo(), "");
    if (isBlank(tipo)) {
      return renderPrompt("welcome_tipo", language, null, Map.of(), buildWelcomeFallback());
    }

    if (isBlank(answers.subtipo())) {
      if ("hogar".equalsIgnoreCase(tipo)) {
        return renderPrompt(
            "ask_subtipo_hogar",
            language,
            tipo,
            Map.of(),
            "¿Eres el propietario del inmueble, o eres inquilino?"
        );
      }
      String list = joinOptions(options.subtypes().get(tipo));
      String formatted = list.isBlank() ? "" : "¿" + list + "?";
      return renderPrompt(
          "ask_subtipo",
          language,
          tipo,
          Map.of("tipo", tipo, "subtipos", formatted),
          "¿Cuál es el nivel de protección que deseas para " + tipo + "? " + formatted
      );
    }

    List<String> usos = options.usages().get(tipo);
    if (usos != null && !usos.isEmpty() && isBlank(answers.uso())) {
      String list = joinOptions(usos);
      String formatted = list.isBlank() ? "" : "¿" + list + "?";
      return renderPrompt(
          "ask_uso",
          language,
          tipo,
          Map.of("usos", formatted),
          "¿Qué uso tiene? " + formatted
      );
    }

    if ("viaje".equalsIgnoreCase(tipo)) {
      if (isBlank(answers.destino())) {
        return renderPrompt(
            "ask_destino",
            language,
            tipo,
            Map.of(),
            "¿Cuál es el destino del viaje?"
        );
      }
    } else if (isBlank(answers.ubicacion())) {
      return renderPrompt(
          "ask_ubicacion",
          language,
          tipo,
          Map.of(),
          "¿En qué ciudad o zona se ubicará el riesgo?"
      );
    }

    return null;
  }

  private String renderPrompt(
      String key,
      String language,
      String tipoSeguro,
      Map<String, String> variables,
      String fallback
  ) {
    String template = promptTemplateService.resolve("step1", key, language, tipoSeguro, fallback);
    return applyTemplate(template, variables);
  }

  private String applyTemplate(String template, Map<String, String> variables) {
    if (template == null || template.isBlank() || variables == null || variables.isEmpty()) {
      return template;
    }
    String output = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String value = entry.getValue() == null ? "" : entry.getValue();
      output = output.replace("{" + entry.getKey() + "}", value);
    }
    return output.replaceAll("\\s+\\?", "?").trim();
  }

  private String buildWelcomeFallback() {
    return "Te damos la bienvenida al validador de seguros, vamos a hacerte algunas preguntas para poder ayudarte mejor.\n\n"
        + "¿Qué tipo de seguro deseas contratar? ¿auto, hogar, salud, viaje?";
  }

  private boolean isCapabilitiesQuestion(String input) {
    if (input == null || input.isBlank()) {
      return false;
    }
    String normalized = normalize(input);
    return normalized.contains("que sabes hacer")
        || normalized.contains("que puedes hacer")
        || normalized.contains("que haces")
        || normalized.contains("que haces aqui")
        || normalized.contains("que haces en este paso")
        || normalized.contains("que puedes hacer por mi")
        || normalized.contains("que puedes hacer por aqui")
        || normalized.contains("que puedes hacer en este paso")
        || normalized.contains("en que me puedes ayudar")
        || normalized.contains("en que me ayudas")
        || normalized.contains("en que puedes ayudarme")
        || normalized.contains("como me puedes ayudar")
        || normalized.contains("como funciona")
        || normalized.contains("como debo responder")
        || normalized.contains("que debo responder")
        || normalized.contains("como respondo")
        || normalized.contains("que tengo que hacer")
        || normalized.contains("que necesitas de mi")
        || normalized.contains("que necesitas")
        || normalized.contains("que informacion necesitas")
        || normalized.contains("que informacion debo dar")
        || normalized.contains("que informacion te doy")
        || normalized.contains("que preguntas haras")
        || normalized.contains("que preguntas haces")
        || normalized.contains("que preguntas vas a hacer")
        || normalized.contains("que pasos sigue")
        || normalized.contains("ayuda");
  }

  private String buildCapabilitiesReply(AiStepTipoAnswers answers, CatalogOptionsResponse options) {
    StringBuilder reply = new StringBuilder();
    reply.append("En este paso me encargo de ayudarte a definir el tipo de seguro que deseas contratar.");
    reply.append("\n\n");
    reply.append("Te iré preguntando por el tipo de seguro (**auto**, **hogar**, **salud**, **viaje**), ");
    reply.append("el nivel de protección (**terceros**, **terceros ampliado**, **todo riesgo**), ");
    reply.append("el uso (**personal**, **profesional**) y la **ciudad o zona** ");
    reply.append("(o el destino si es **viaje**).");
    reply.append("\n\n");
    reply.append("Con tus respuestas iré completando el resumen y, cuando esté listo, ");
    reply.append("avanzaremos al siguiente paso para recoger los datos de riesgo.");

    String nextQuestion = buildNextQuestion(answers, options, "es");
    if (nextQuestion != null && !nextQuestion.isBlank()) {
      reply.append("\n\nPara continuar: ");
      reply.append(boldKeywords(nextQuestion));
    }
    return reply.toString();
  }

  private String boldKeywords(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String result = text;
    for (String keyword : this.boldKeywords) {
      String pattern = "(?i)\\b" + Pattern.quote(keyword) + "\\b";
      result = result.replaceAll(pattern, "**$0**");
    }
    return result;
  }

  private String joinOptions(List<String> options) {
    if (options == null || options.isEmpty()) {
      return "";
    }
    return String.join(", ", options);
  }

  private String coalesce(String candidate, String fallback) {
    return isBlank(candidate) ? fallback : candidate;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String matchTipo(String input) {
    return matchOption(input, List.of("auto", "hogar", "salud", "viaje"));
  }

  private String matchOption(String input, List<String> options) {
    if (options == null || options.isEmpty() || input == null) {
      return null;
    }
    String normalizedInput = normalize(input);
    if (normalizedInput.isBlank()) {
      return null;
    }
    String synonymMatch = matchSynonym(normalizedInput, options);
    if (synonymMatch != null) {
      return synonymMatch;
    }
    List<String> partialMatches = new ArrayList<>();
    for (String option : options) {
      String normalizedOption = normalize(option);
      if (normalizedInput.equals(normalizedOption) || normalizedInput.contains(normalizedOption)) {
        return option;
      }
      if (normalizedInput.length() >= 4 && containsAllTokens(normalizedOption, normalizedInput)) {
        partialMatches.add(option);
      }
    }
    if (partialMatches.size() == 1) {
      return partialMatches.get(0);
    }
    return null;
  }

  private String matchSynonym(String normalizedInput, List<String> options) {
    List<String> matches = new ArrayList<>();
    for (String option : options) {
      List<String> synonyms = synonymsForOption(option);
      if (synonyms.isEmpty()) {
        continue;
      }
      for (String synonym : synonyms) {
        String normalizedSynonym = normalize(synonym);
        if (normalizedSynonym.isBlank()) {
          continue;
        }
        boolean match = normalizedInput.equals(normalizedSynonym)
            || normalizedInput.contains(normalizedSynonym)
            || normalizedSynonym.contains(normalizedInput);
        if (match) {
          matches.add(option);
          break;
        }
      }
    }
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private List<String> synonymsForOption(String option) {
    if (synonymsMap == null || synonymsMap.isEmpty() || option == null) {
      return List.of();
    }
    String normalizedOption = normalize(option);
    for (Map.Entry<String, List<String>> entry : synonymsMap.entrySet()) {
      if (normalize(entry.getKey()).equals(normalizedOption)) {
        return entry.getValue();
      }
    }
    return List.of();
  }

  private boolean containsAllTokens(String normalizedOption, String normalizedInput) {
    Set<String> optionTokens = new HashSet<>(Arrays.asList(normalizedOption.split(" ")));
    for (String token : normalizedInput.split(" ")) {
      if (token.isBlank()) {
        continue;
      }
      if (!optionTokens.contains(token)) {
        return false;
      }
    }
    return true;
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ");
    return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
  }

  private String detectLanguage(String input) {
    if (input == null || input.isBlank()) {
      return "es";
    }
    String normalized = normalize(input);
    int scoreEs = scoreLanguage(normalized, List.of(
        "que", "como", "seguro", "hogar", "salud", "viaje", "uso", "ciudad",
        "zona", "propietario", "inquilino", "residencia", "vivienda"
    ));
    int scoreEn = scoreLanguage(normalized, List.of(
        "what", "how", "insurance", "home", "house", "health", "travel", "use",
        "city", "area", "owner", "tenant", "primary", "secondary"
    ));
    int scorePt = scoreLanguage(normalized, List.of(
        "seguro", "casa", "saude", "viagem", "uso", "cidade", "zona",
        "proprietario", "inquilino", "moradia"
    ));
    int scoreFr = scoreLanguage(normalized, List.of(
        "assurance", "maison", "sante", "voyage", "utilisation", "ville",
        "zone", "proprietaire", "locataire"
    ));
    int scoreIt = scoreLanguage(normalized, List.of(
        "assicurazione", "casa", "salute", "viaggio", "uso", "citta", "zona",
        "proprietario", "inquilino"
    ));

    int max = Math.max(scoreEs, Math.max(scoreEn, Math.max(scorePt, Math.max(scoreFr, scoreIt))));
    if (max == 0) {
      return "es";
    }
    if (max == scoreEn) {
      return "en";
    }
    if (max == scorePt) {
      return "pt";
    }
    if (max == scoreFr) {
      return "fr";
    }
    if (max == scoreIt) {
      return "it";
    }
    return "es";
  }

  private int scoreLanguage(String normalized, List<String> markers) {
    int score = 0;
    for (String marker : markers) {
      if (normalized.contains(marker)) {
        score++;
      }
    }
    return score;
  }

  private String buildFinalSummary(
      AiStepTipoAnswers answers,
      String language,
      CatalogOptionsResponse options
  ) {
    String tipo = isBlank(answers.tipo()) ? "seguro" : "seguro de " + answers.tipo();
    StringBuilder builder = new StringBuilder();
    builder.append("Tenemos lo necesario, puedes ir al siguiente paso: Datos del riesgo. ");
    builder.append("He registrado el ");
    builder.append(tipo);
    if (!isBlank(answers.subtipo())) {
      builder.append(" con nivel de protección ");
      builder.append(answers.subtipo());
    }
    if (!isBlank(answers.uso())) {
      builder.append(", uso ");
      builder.append(answers.uso());
    }
    if ("viaje".equalsIgnoreCase(answers.tipo())) {
      if (!isBlank(answers.destino())) {
        builder.append(", destino ");
        builder.append(answers.destino());
      }
    } else if (!isBlank(answers.ubicacion())) {
      builder.append(", ubicación ");
      builder.append(answers.ubicacion());
    }
    builder.append(".");
    return builder.toString();
  }

  private void streamText(String text, java.util.function.Consumer<String> onToken) {
    if (text == null || text.isBlank() || onToken == null) {
      return;
    }
    int chunkSize = 10;
    for (int i = 0; i < text.length(); i += chunkSize) {
      int end = Math.min(i + chunkSize, text.length());
      onToken.accept(text.substring(i, end));
    }
  }
}
