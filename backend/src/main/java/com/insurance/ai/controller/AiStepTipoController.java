package com.insurance.ai.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.insurance.ai.dto.AiStepTipoRequest;
import com.insurance.ai.dto.AiStepTipoResponse;
import com.insurance.ai.service.AiStepTipoService;

@RestController
@RequestMapping("/ai")
@Validated
public class AiStepTipoController {
  private final AiStepTipoService aiStepTipoService;

  public AiStepTipoController(AiStepTipoService aiStepTipoService) {
    this.aiStepTipoService = aiStepTipoService;
  }

  @PostMapping("/step-tipo")
  public AiStepTipoResponse stepTipo(@RequestBody AiStepTipoRequest request) {
    return aiStepTipoService.getNext(request);
  }

  @PostMapping(value = "/step-tipo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stepTipoStream(@RequestBody AiStepTipoRequest request) {
    SseEmitter emitter = new SseEmitter(0L);

    CompletableFuture.runAsync(() -> {
      try {
        AiStepTipoResponse response = aiStepTipoService.streamNext(request, token -> {
          try {
            emitter.send(SseEmitter.event().data(new StreamEvent("token", token)));
          } catch (Exception sendError) {
            emitter.completeWithError(sendError);
          }
        });
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
