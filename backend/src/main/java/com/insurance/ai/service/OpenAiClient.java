package com.insurance.ai.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAiClient {
  private final RestTemplate restTemplate = new RestTemplate();
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final String apiKey;
  private final String baseUrl;
  private final ObjectMapper objectMapper;

  public OpenAiClient(
      @Value("${OPENAI_API_KEY}") String apiKey,
      @Value("${OPENAI_API_URL:https://api.openai.com/v1}") String baseUrl,
      ObjectMapper objectMapper
  ) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.objectMapper = objectMapper;
  }

  public String chat(String model, List<Message> messages, double temperature, String outputFormat) {
    ResponseFormat responseFormat = null;
    if (outputFormat != null && outputFormat.equalsIgnoreCase("json_object")) {
      responseFormat = new ResponseFormat("json_object");
    }

    ChatCompletionRequest request = new ChatCompletionRequest(model, messages, temperature, responseFormat, null);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);

    HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);
    ChatCompletionResponse response = restTemplate.postForObject(
        baseUrl + "/chat/completions",
        entity,
        ChatCompletionResponse.class
    );

    if (response == null || response.choices == null || response.choices.isEmpty()) {
      throw new IllegalStateException("OpenAI response is empty");
    }

    return response.choices.get(0).message.content;
  }

  public String streamChat(
      String model,
      List<Message> messages,
      double temperature,
      Consumer<String> onToken
  ) {
    try {
      ChatCompletionRequest request = new ChatCompletionRequest(model, messages, temperature, null, true);
      String body = objectMapper.writeValueAsString(request);
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/chat/completions"))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .header("Accept", "text/event-stream")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      HttpResponse<java.io.InputStream> response = httpClient.send(
          httpRequest,
          HttpResponse.BodyHandlers.ofInputStream()
      );

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("OpenAI streaming error: HTTP " + response.statusCode());
      }

      StringBuilder full = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.startsWith("data:")) {
            continue;
          }
          String data = line.substring(5).trim();
          if (data.isBlank()) {
            continue;
          }
          if ("[DONE]".equals(data)) {
            break;
          }
          ChatCompletionStreamResponse chunk = objectMapper.readValue(data, ChatCompletionStreamResponse.class);
          if (chunk == null || chunk.choices == null || chunk.choices.isEmpty()) {
            continue;
          }
          String delta = chunk.choices.get(0).delta.content;
          if (delta != null && !delta.isEmpty()) {
            full.append(delta);
            if (onToken != null) {
              onToken.accept(delta);
            }
          }
        }
      }

      return full.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("OpenAI streaming failed", ex);
    }
  }

  public record Message(
      String role,
      String content
  ) {}

  public record ResponseFormat(
      @JsonProperty("type") String type
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ChatCompletionRequest(
      String model,
      List<Message> messages,
      @JsonProperty("temperature") double temperature,
      @JsonProperty("response_format") ResponseFormat responseFormat,
      @JsonProperty("stream") Boolean stream
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ChatCompletionResponse {
    public List<Choice> choices;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {
    public MessageResponse message;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MessageResponse {
    public String content;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ChatCompletionStreamResponse {
    public List<StreamChoice> choices;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class StreamChoice {
    public StreamDelta delta;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class StreamDelta {
    public String content;
  }
}
