package com.insurance.ai.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.insurance.ai.dto.AiStepPropuestaRequest;
import com.insurance.ai.dto.AiStepPropuestaResponse;
import com.insurance.ai.dto.AiStepPropuestaSaveResponse;
import com.insurance.ai.service.AiStepPropuestaService;

@RestController
@RequestMapping("/ai")
@Validated
public class AiStepPropuestaController {
  private final AiStepPropuestaService aiStepPropuestaService;

  public AiStepPropuestaController(AiStepPropuestaService aiStepPropuestaService) {
    this.aiStepPropuestaService = aiStepPropuestaService;
  }

  @PostMapping("/step-propuesta")
  public AiStepPropuestaResponse stepPropuesta(@RequestBody AiStepPropuestaRequest request) {
    return aiStepPropuestaService.getPropuesta(request);
  }

  @PostMapping(value = "/step-propuesta/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stepPropuestaStream(@RequestBody AiStepPropuestaRequest request) {
    SseEmitter emitter = new SseEmitter(0L);

    CompletableFuture.runAsync(() -> {
      try {
        AiStepPropuestaResponse response = aiStepPropuestaService.streamPropuesta(
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

  @PostMapping(value = "/step-propuesta/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> stepPropuestaPdf(@RequestBody AiStepPropuestaRequest request) {
    byte[] pdf = aiStepPropuestaService.generatePdf(request);
    String filename = "propuesta-" + System.currentTimeMillis() + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @PostMapping("/step-propuesta/save")
  public AiStepPropuestaSaveResponse stepPropuestaSave(@RequestBody AiStepPropuestaRequest request) {
    String filename = aiStepPropuestaService.savePdf(request);
    String status = filename == null ? "error" : "saved";
    return new AiStepPropuestaSaveResponse(status, filename);
  }

  @GetMapping(value = "/step-propuesta/pdf/{filename}", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> stepPropuestaPdfFile(@PathVariable String filename) {
    byte[] pdf = aiStepPropuestaService.loadSavedPdf(filename);
    if (pdf == null || pdf.length == 0) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  public record StreamEvent(String type, Object value) {}
}
