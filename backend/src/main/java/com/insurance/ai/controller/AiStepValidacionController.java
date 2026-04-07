package com.insurance.ai.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.insurance.ai.dto.AiStepValidacionRequest;
import com.insurance.ai.dto.AiStepValidacionResponse;
import com.insurance.ai.service.AiStepValidacionService;

@RestController
@RequestMapping("/ai")
@Validated
public class AiStepValidacionController {
  private final AiStepValidacionService aiStepValidacionService;

  public AiStepValidacionController(AiStepValidacionService aiStepValidacionService) {
    this.aiStepValidacionService = aiStepValidacionService;
  }

  @PostMapping("/step-validacion")
  public AiStepValidacionResponse stepValidacion(@RequestBody AiStepValidacionRequest request) {
    return aiStepValidacionService.getValidation(request);
  }

  @PostMapping(value = "/step-validacion/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stepValidacionStream(@RequestBody AiStepValidacionRequest request) {
    SseEmitter emitter = new SseEmitter(0L);

    CompletableFuture.runAsync(() -> {
      try {
        AiStepValidacionResponse response = aiStepValidacionService.streamValidation(
            request,
            token -> {
              try {
                emitter.send(SseEmitter.event().data(new StreamEvent("token", token)));
              } catch (Exception sendError) {
                emitter.completeWithError(sendError);
              }
            }
        );
        emitter.send(SseEmitter.event().data(new StreamEvent("final", response)));
        emitter.complete();
      } catch (Exception ex) {
        emitter.completeWithError(ex);
      }
    });

    return emitter;
  }

  public record StreamEvent(String type, Object value) {}
}
