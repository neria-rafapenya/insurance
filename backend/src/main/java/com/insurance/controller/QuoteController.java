package com.insurance.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.insurance.model.dto.InitResponse;
import com.insurance.model.dto.PricingResponse;
import com.insurance.model.dto.QuoteRequest;
import com.insurance.model.dto.ValidationResponse;
import com.insurance.service.QuoteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/quote")
@Validated
public class QuoteController {
  private final QuoteService quoteService;

  public QuoteController(QuoteService quoteService) {
    this.quoteService = quoteService;
  }

  @PostMapping("/init")
  public InitResponse init() {
    return quoteService.init();
  }

  @PostMapping("/update")
  public ValidationResponse update(@Valid @RequestBody QuoteRequest request) {
    return quoteService.validate(request);
  }

  @PostMapping("/validate")
  public ValidationResponse validate(@Valid @RequestBody QuoteRequest request) {
    return quoteService.validate(request);
  }

  @PostMapping("/calculate")
  public PricingResponse calculate(@Valid @RequestBody QuoteRequest request) {
    return quoteService.calculate(request);
  }

  @GetMapping("/result")
  public ResponseEntity<Map<String, String>> result() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(Map.of("message", "Use /quote/calculate for the current result"));
  }
}
