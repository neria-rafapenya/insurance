package com.insurance.ai.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.ai.dto.AiPropuestaDetalle;
import com.insurance.ai.dto.AiPropuestaResult;
import com.insurance.ai.dto.AiStepPropuestaRequest;
import com.insurance.ai.dto.AiStepPropuestaResponse;
import com.insurance.ai.dto.AiHealthMember;
import com.insurance.ai.dto.AiStepRiesgoAnswers;
import com.insurance.ai.dto.AiStepTipoAnswers;
import com.insurance.ai.dto.AiValidationResult;
import com.insurance.model.Coverage;
import com.insurance.model.QuoteStatus;
import com.insurance.model.dto.PricingResponse;
import com.insurance.model.dto.QuoteRequest;
import com.insurance.service.QuoteService;

@Service
public class AiStepPropuestaService {
  private static final int PRIMARY_RED = 221;
  private static final int PRIMARY_GREEN = 47;
  private static final int PRIMARY_BLUE = 88;
  private static final float LOGO_VIEWBOX_WIDTH = 104f;
  private static final float LOGO_VIEWBOX_HEIGHT = 121f;
  private static final String LOGO_PATH_1 =
      "M104 18.1504V55.0547C104 70.3814 99.0989 84.3213 89.2969 96.873C79.4906 109.429 "
          + "67.0582 117.471 52 121C36.9417 117.471 24.511 109.429 14.709 96.873C4.90267 "
          + "84.3213 0 70.3814 0 55.0547V18.1504L52 0L104 18.1504ZM13 26.4688V55.0547C13 "
          + "67.2555 16.6832 78.3476 24.0498 88.3301C31.4165 98.3126 40.7334 104.967 52 "
          + "108.295C63.2666 104.967 72.5836 98.3125 79.9502 88.3301C87.3168 78.3476 91 "
          + "67.2555 91 55.0547V26.4688L52 12.8564L13 26.4688Z";
  private static final String LOGO_PATH_2 =
      "M83.5576 62.6553C81.0845 80.3547 68.4345 95.8824 52 100C46.749 98.6844 "
          + "41.8854 96.2023 37.6152 92.8584L83.5576 62.6553Z";
  private static final String LOGO_PATH_3 =
      "M84 56.3604C84 56.7708 83.9917 57.1806 83.9805 57.5898L34.4688 90.1396C31.9171 "
          + "87.71 29.6364 84.9523 27.6807 81.9482L84 44.4023V56.3604Z";
  private static final String LOGO_PATH_4 =
      "M84 32V39.5957L25.6387 78.5029C22.0538 71.8279 20.0001 64.1967 20 56.3604V32L52 "
          + "20L84 32Z";

  private final OpenAiClient openAiClient;
  private final QuoteService quoteService;
  private final RiskFactorService riskFactorService;
  private final ObjectMapper objectMapper;
  private final String model;
  private final double temperature;
  private final String systemPrompt;

  public AiStepPropuestaService(
      OpenAiClient openAiClient,
      QuoteService quoteService,
      RiskFactorService riskFactorService,
      ObjectMapper objectMapper,
      @Value("${ai.model}") String model,
      @Value("${ai.temperature}") double temperature,
      @Value("${ai.propuesta-prompt-file:classpath:prompts/propuesta.md}") Resource promptResource
  ) {
    this.openAiClient = openAiClient;
    this.quoteService = quoteService;
    this.riskFactorService = riskFactorService;
    this.objectMapper = objectMapper;
    this.model = model;
    this.temperature = temperature;
    this.systemPrompt = loadPrompt(promptResource, defaultPrompt());
  }

  public AiStepPropuestaResponse getPropuesta(AiStepPropuestaRequest request) {
    AiStepTipoAnswers step1 = request.step1() == null ? AiStepTipoAnswers.empty() : request.step1();
    AiStepRiesgoAnswers step2 = request.step2();
    AiValidationResult validation = request.validation();
    String variant = normalizeVariant(request.variant());
    AiPropuestaResult result = buildResult(step1, step2, validation, request.selectedCoverages(), variant);
    String reply = buildExplanation(step1, step2, validation, result, variant);
    return new AiStepPropuestaResponse(reply, result);
  }

  public AiStepPropuestaResponse streamPropuesta(
      AiStepPropuestaRequest request,
      java.util.function.Consumer<String> onToken
  ) {
    AiStepTipoAnswers step1 = request.step1() == null ? AiStepTipoAnswers.empty() : request.step1();
    AiStepRiesgoAnswers step2 = request.step2();
    AiValidationResult validation = request.validation();
    String variant = normalizeVariant(request.variant());
    AiPropuestaResult result = buildResult(step1, step2, validation, request.selectedCoverages(), variant);
    String reply = buildExplanation(step1, step2, validation, result, variant, onToken);
    return new AiStepPropuestaResponse(reply, result);
  }

  public byte[] generatePdf(AiStepPropuestaRequest request) {
    AiStepTipoAnswers step1 = request.step1() == null ? AiStepTipoAnswers.empty() : request.step1();
    AiStepRiesgoAnswers step2 = request.step2();
    AiValidationResult validation = request.validation();
    String variant = normalizeVariant(request.variant());
    AiPropuestaResult result = buildResult(step1, step2, validation, request.selectedCoverages(), variant);
    String reply = buildExplanation(step1, step2, validation, result, variant);
    AiStepPropuestaResponse response = new AiStepPropuestaResponse(reply, result);
    return renderPdf(response, variant);
  }

  public String savePdf(AiStepPropuestaRequest request) {
    byte[] pdf = generatePdf(request);
    if (pdf.length == 0) {
      return null;
    }
    String filename = "propuesta-" + System.currentTimeMillis() + ".pdf";
    Path dir = Paths.get(System.getProperty("user.dir"), "storage", "propuestas");
    try {
      Files.createDirectories(dir);
      Files.write(dir.resolve(filename), pdf);
      return filename;
    } catch (IOException ex) {
      return null;
    }
  }

  public byte[] loadSavedPdf(String filename) {
    if (filename == null || filename.isBlank()) {
      return null;
    }
    if (!filename.startsWith("propuesta-") || filename.contains("..")
        || filename.contains("/") || filename.contains("\\")) {
      return null;
    }
    Path dir = Paths.get(System.getProperty("user.dir"), "storage", "propuestas");
    Path file = dir.resolve(filename).normalize();
    if (!file.startsWith(dir)) {
      return null;
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException ex) {
      return null;
    }
  }

  private AiPropuestaResult buildResult(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult validation,
      List<String> selectedCoverages,
      String variant
  ) {
    String tipoSeguro = normalize(step1.tipo());
    Map<String, Object> data = buildData(tipoSeguro, step1, step2);
    PricingResponse pricing = quoteService.calculate(new QuoteRequest(tipoSeguro, data));

    List<Coverage> coverages = pricing.coverages() == null
        ? List.of()
        : pricing.coverages().stream().map(dto -> {
          Coverage coverage = new Coverage();
          coverage.setNombre(dto.nombre());
          coverage.setIncluido(dto.incluido());
          coverage.setPrecioExtra(dto.precioExtra());
          return coverage;
        }).toList();

    List<String> incluidas = coverages.stream()
        .filter(Coverage::getIncluido)
        .map(Coverage::getNombre)
        .toList();

    List<String> selected = selectedCoverages == null ? List.of() : selectedCoverages;
    List<Coverage> opcionalCoverages = coverages.stream()
        .filter(cov -> !Boolean.TRUE.equals(cov.getIncluido()))
        .toList();

    List<String> effectiveSelected = switch (variant) {
      case "basic" -> List.of();
      case "premium" -> opcionalCoverages.stream().map(Coverage::getNombre).toList();
      default -> selected;
    };

    List<String> normalizedSelected = effectiveSelected.stream()
        .map(this::normalize)
        .collect(Collectors.toList());

    List<String> opcionales = opcionalCoverages.stream()
        .filter(cov -> normalizedSelected.stream().anyMatch(sel -> sel.equals(normalize(cov.getNombre()))))
        .map(Coverage::getNombre)
        .toList();

    double extras = opcionalCoverages.stream()
        .filter(cov -> normalizedSelected.stream().anyMatch(sel -> sel.equals(normalize(cov.getNombre()))))
        .mapToDouble(cov -> parsePrice(cov.getPrecioExtra()))
        .sum();

    double base = pricing.basePrice() == null ? 0.0 : pricing.basePrice();
    double finalPrice = pricing.finalPrice() == null ? base : pricing.finalPrice();
    double recargos = Math.max(finalPrice - base, 0.0);
    double total = finalPrice + extras;

    String estado = resolveEstado(validation, pricing.status());
    List<String> condiciones = buildCondiciones(validation, pricing.messages());

    AiPropuestaDetalle detalle = new AiPropuestaDetalle(
        roundMoney(base),
        roundMoney(recargos),
        roundMoney(extras),
        roundMoney(total)
    );
    return new AiPropuestaResult(
        estado,
        roundMoney(total),
        roundMoney(total / 12.0),
        detalle,
        incluidas,
        opcionales,
        condiciones
    );
  }

  private String buildExplanation(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult validation,
      AiPropuestaResult result,
      String variant
  ) {
    return buildExplanation(step1, step2, validation, result, variant, null);
  }

  private String buildExplanation(
      AiStepTipoAnswers step1,
      AiStepRiesgoAnswers step2,
      AiValidationResult validation,
      AiPropuestaResult result,
      String variant,
      java.util.function.Consumer<String> onToken
  ) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("language", "es");
    payload.put("variant", variant);
    payload.put("step1", step1);
    payload.put("step2", step2);
    payload.put("validation", validation);
    payload.put("result", result);

    String body;
    try {
      body = objectMapper.writeValueAsString(payload);
    } catch (Exception ex) {
      return fallbackExplanation(result);
    }

    List<OpenAiClient.Message> messages = List.of(
        new OpenAiClient.Message("system", systemPrompt),
        new OpenAiClient.Message("user", body)
    );

    try {
      if (onToken == null) {
        return openAiClient.chat(model, messages, temperature, null);
      }
      return openAiClient.streamChat(model, messages, temperature, onToken);
    } catch (Exception ex) {
      return fallbackExplanation(result);
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

  private List<AiHealthMember> resolveHealthMembers(AiStepRiesgoAnswers step2) {
    if (step2 == null) {
      return List.of();
    }
    if (step2.healthFamilyMembers() != null && !step2.healthFamilyMembers().isEmpty()) {
      return step2.healthFamilyMembers();
    }
    List<AiHealthMember> members = new ArrayList<>();
    if (step2.healthFamilyDetails() != null && !step2.healthFamilyDetails().isBlank()) {
      for (String part : step2.healthFamilyDetails().split("[^0-9]+")) {
        if (part.isBlank()) {
          continue;
        }
        try {
          members.add(new AiHealthMember(null, Integer.parseInt(part), null, null));
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
    }
    if (members.isEmpty() && step2.healthAge() != null) {
      members.add(new AiHealthMember(null, step2.healthAge(), step2.healthSmoker(), step2.healthPathologies()));
    }
    return members;
  }

  private Integer resolveHealthMaxAge(AiStepRiesgoAnswers step2, List<AiHealthMember> members) {
    Integer familyMax = members.stream()
        .map(AiHealthMember::edad)
        .filter(Objects::nonNull)
        .max(Integer::compareTo)
        .orElse(null);
    if (familyMax != null) {
      return familyMax;
    }
    if (step2 == null) {
      return null;
    }
    Integer detailsMax = extractMaxAge(step2.healthFamilyDetails());
    if (detailsMax != null) {
      return detailsMax;
    }
    return step2.healthAge();
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

  private String resolveEstado(AiValidationResult validation, QuoteStatus status) {
    if (validation != null && validation.estado() != null) {
      return validation.estado();
    }
    if (status == QuoteStatus.REJECT) {
      return "reject";
    }
    if (status == QuoteStatus.SURCHARGE) {
      return "warning";
    }
    return "ok";
  }

  private List<String> buildCondiciones(AiValidationResult validation, List<String> pricingMessages) {
    List<String> items = new ArrayList<>();
    if (validation != null) {
      items.addAll(validation.incidencias());
      items.addAll(validation.restricciones());
      items.addAll(validation.recargos());
    }
    if (pricingMessages != null) {
      for (String message : pricingMessages) {
        if (!items.contains(message)) {
          items.add(message);
        }
      }
    }
    return items;
  }

  private byte[] renderPdf(AiStepPropuestaResponse response, String variant) {
    try (PDDocument document = new PDDocument()) {
      PDFont font = PDType1Font.HELVETICA;
      float fontSize = 12f;
      float leading = 16f;
      float margin = 48f;
      AiPropuestaResult result = response.result();

      List<String> lines = new ArrayList<>();
      lines.add("Resultado y propuesta");
      lines.add("Variante: " + variantLabel(variant));
      lines.add("");
      lines.add("Estado: " + estadoLabel(result.estado()));
      lines.add(String.format(Locale.ROOT, "Precio total: %.2f € (%.2f €/mes)",
          result.precioTotal(), result.precioMensual()));
      lines.add("");
      lines.add("Desglose:");
      lines.add(String.format(Locale.ROOT, "  Base: %.2f €", result.detalle().base()));
      lines.add(String.format(Locale.ROOT, "  Recargos: +%.2f €", result.detalle().recargos()));
      lines.add(String.format(Locale.ROOT, "  Coberturas: +%.2f €", result.detalle().extras()));
      lines.add(String.format(Locale.ROOT, "  Total: %.2f €", result.detalle().total()));
      lines.add("");
      lines.add("Coberturas incluidas:");
      if (result.coberturasIncluidas().isEmpty()) {
        lines.add("  - Ninguna");
      } else {
        result.coberturasIncluidas().forEach(item -> lines.add("  - " + item));
      }
      lines.add("");
      lines.add("Coberturas opcionales seleccionadas:");
      if (result.coberturasOpcionales().isEmpty()) {
        lines.add("  - Ninguna");
      } else {
        result.coberturasOpcionales().forEach(item -> lines.add("  - " + item));
      }
      lines.add("");
      lines.add("Advertencias / condiciones:");
      if (result.condiciones().isEmpty()) {
        lines.add("  - Sin condiciones relevantes");
      } else {
        result.condiciones().forEach(item -> lines.add("  - " + item));
      }
      lines.add("");
      lines.add("Resumen y recomendación:");
      String plainReply = stripMarkdown(response.reply());
      for (String paragraph : plainReply.split("\\r?\\n")) {
        if (paragraph.isBlank()) {
          lines.add("");
        } else {
          lines.add(paragraph.trim());
        }
      }

      try (PdfWriter writer = new PdfWriter(
          document,
          font,
          fontSize,
          leading,
          margin,
          (content, page) -> drawHeader(content, page)
      )) {
        for (String line : lines) {
          writer.writeWrapped(line);
        }
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException ex) {
      return new byte[0];
    }
  }

  private String stripMarkdown(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace("#", "")
        .replace("*", "")
        .trim();
  }

  private String normalizeVariant(String value) {
    String normalized = normalize(value);
    if (normalized.contains("basic") || normalized.contains("basica")) {
      return "basic";
    }
    if (normalized.contains("premium")) {
      return "premium";
    }
    return "optimized";
  }

  private String variantLabel(String variant) {
    return switch (variant) {
      case "basic" -> "Básica";
      case "premium" -> "Premium";
      default -> "Optimizada";
    };
  }

  private String estadoLabel(String estado) {
    return switch (estado) {
      case "reject" -> "No asegurable";
      case "warning" -> "Asegurable con condiciones";
      default -> "Asegurable";
    };
  }

  private interface HeaderDrawer {
    float draw(PDPageContentStream content, PDPage page) throws IOException;
  }

  private static final class PdfWriter implements AutoCloseable {
    private final PDDocument document;
    private final PDFont font;
    private final float fontSize;
    private final float leading;
    private final float margin;
    private final HeaderDrawer headerDrawer;
    private PDPage page;
    private PDPageContentStream content;
    private float y;
    private float maxWidth;

    PdfWriter(
        PDDocument document,
        PDFont font,
        float fontSize,
        float leading,
        float margin,
        HeaderDrawer headerDrawer
    )
        throws IOException {
      this.document = document;
      this.font = font;
      this.fontSize = fontSize;
      this.leading = leading;
      this.margin = margin;
      this.headerDrawer = headerDrawer;
      newPage();
    }

    void writeWrapped(String text) throws IOException {
      if (text == null || text.isEmpty()) {
        newLine();
        return;
      }
      for (String line : wrapLine(text, font, fontSize, maxWidth)) {
        ensureSpace();
        content.showText(line);
        newLine();
      }
    }

    private void newLine() throws IOException {
      content.newLineAtOffset(0, -leading);
      y -= leading;
    }

    private void ensureSpace() throws IOException {
      if (y - leading < margin) {
        newPage();
      }
    }

    private void newPage() throws IOException {
      if (content != null) {
        content.endText();
        content.close();
      }
      page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      maxWidth = page.getMediaBox().getWidth() - (margin * 2);
      content = new PDPageContentStream(document, page);
      float startY = page.getMediaBox().getHeight() - margin;
      if (headerDrawer != null) {
        startY = headerDrawer.draw(content, page);
      }
      y = startY;
      content.setNonStrokingColor(0, 0, 0);
      content.setFont(font, fontSize);
      content.beginText();
      content.newLineAtOffset(margin, y);
    }

    @Override
    public void close() throws IOException {
      if (content != null) {
        content.endText();
        content.close();
      }
    }
  }

  private static List<String> wrapLine(String text, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    if (text == null || text.isBlank()) {
      return List.of("");
    }
    List<String> lines = new ArrayList<>();
    String[] words = text.split("\\s+");
    StringBuilder current = new StringBuilder();
    for (String word : words) {
      if (current.length() == 0) {
        current.append(word);
        continue;
      }
      String candidate = current + " " + word;
      float width = font.getStringWidth(candidate) / 1000f * fontSize;
      if (width <= maxWidth) {
        current.append(" ").append(word);
      } else {
        lines.add(current.toString());
        current = new StringBuilder(word);
      }
    }
    if (current.length() > 0) {
      lines.add(current.toString());
    }
    return lines;
  }

  private float drawHeader(PDPageContentStream content, PDPage page) throws IOException {
    float pageWidth = page.getMediaBox().getWidth();
    float pageHeight = page.getMediaBox().getHeight();
    float margin = 48f;
    float logoWidth = 36f;
    float logoHeight = logoWidth * (LOGO_VIEWBOX_HEIGHT / LOGO_VIEWBOX_WIDTH);
    float headerTop = pageHeight - margin;
    float logoX = margin;
    float logoY = headerTop - logoHeight;
    float textX = logoX + logoWidth + 12f;
    float textY = headerTop - 18f;

    content.setNonStrokingColor(PRIMARY_RED, PRIMARY_GREEN, PRIMARY_BLUE);
    drawLogo(content, logoX, logoY, logoWidth);

    content.beginText();
    content.setFont(PDType1Font.HELVETICA_BOLD, 16f);
    content.newLineAtOffset(textX, textY);
    content.showText("Validador de seguros");
    content.endText();

    float lineY = logoY - 10f;
    content.setStrokingColor(PRIMARY_RED, PRIMARY_GREEN, PRIMARY_BLUE);
    content.setLineWidth(3f);
    content.moveTo(margin, lineY);
    content.lineTo(pageWidth - margin, lineY);
    content.stroke();

    return lineY - 18f;
  }

  private void drawLogo(PDPageContentStream content, float x, float y, float width)
      throws IOException {
    float scale = width / LOGO_VIEWBOX_WIDTH;
    drawSvgPath(content, LOGO_PATH_1, x, y, scale, true);
    drawSvgPath(content, LOGO_PATH_2, x, y, scale, false);
    drawSvgPath(content, LOGO_PATH_3, x, y, scale, false);
    drawSvgPath(content, LOGO_PATH_4, x, y, scale, false);
  }

  private void drawSvgPath(
      PDPageContentStream content,
      String path,
      float offsetX,
      float offsetY,
      float scale,
      boolean evenOdd
  ) throws IOException {
    String normalized = path.replace(",", " ").replaceAll("([A-Za-z])", " $1 ").trim();
    if (normalized.isEmpty()) {
      return;
    }
    String[] tokens = normalized.split("\\s+");
    String cmd = null;
    double cx = 0;
    double cy = 0;
    int i = 0;
    String pending = null;
    while (i < tokens.length || pending != null) {
      String token;
      if (pending != null) {
        token = pending;
        pending = null;
      } else {
        token = tokens[i++];
      }
      if (token.length() == 1 && Character.isLetter(token.charAt(0))) {
        cmd = token;
        if ("Z".equalsIgnoreCase(cmd)) {
          content.closePath();
        }
        continue;
      }
      if (cmd == null) {
        continue;
      }
      switch (cmd) {
        case "M" -> {
          double x = Double.parseDouble(token);
          double y = Double.parseDouble(tokens[i++]);
          float[] pt = transformPoint(x, y, offsetX, offsetY, scale);
          content.moveTo(pt[0], pt[1]);
          cx = x;
          cy = y;
          cmd = "L";
        }
        case "L" -> {
          double x = Double.parseDouble(token);
          double y = Double.parseDouble(tokens[i++]);
          float[] pt = transformPoint(x, y, offsetX, offsetY, scale);
          content.lineTo(pt[0], pt[1]);
          cx = x;
          cy = y;
        }
        case "H" -> {
          double x = Double.parseDouble(token);
          float[] pt = transformPoint(x, cy, offsetX, offsetY, scale);
          content.lineTo(pt[0], pt[1]);
          cx = x;
        }
        case "V" -> {
          double y = Double.parseDouble(token);
          float[] pt = transformPoint(cx, y, offsetX, offsetY, scale);
          content.lineTo(pt[0], pt[1]);
          cy = y;
        }
        case "C" -> {
          double x1 = Double.parseDouble(token);
          double y1 = Double.parseDouble(tokens[i++]);
          double x2 = Double.parseDouble(tokens[i++]);
          double y2 = Double.parseDouble(tokens[i++]);
          double x3 = Double.parseDouble(tokens[i++]);
          double y3 = Double.parseDouble(tokens[i++]);
          float[] p1 = transformPoint(x1, y1, offsetX, offsetY, scale);
          float[] p2 = transformPoint(x2, y2, offsetX, offsetY, scale);
          float[] p3 = transformPoint(x3, y3, offsetX, offsetY, scale);
          content.curveTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]);
          cx = x3;
          cy = y3;
        }
        default -> {
          // ignore unsupported command
        }
      }
    }
    if (evenOdd) {
      content.fillEvenOdd();
    } else {
      content.fill();
    }
  }

  private float[] transformPoint(
      double x,
      double y,
      float offsetX,
      float offsetY,
      float scale
  ) {
    float px = offsetX + (float) x * scale;
    float py = offsetY + (LOGO_VIEWBOX_HEIGHT - (float) y) * scale;
    return new float[] { px, py };
  }

  private double parsePrice(String value) {
    if (value == null || value.isBlank()) {
      return 0.0;
    }
    String normalized = value.replaceAll("[^0-9,.-]", "").replace(",", ".");
    try {
      return Double.parseDouble(normalized);
    } catch (NumberFormatException ex) {
      return 0.0;
    }
  }

  private double roundMoney(double value) {
    return Math.round(value * 100.0) / 100.0;
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

  private String fallbackExplanation(AiPropuestaResult result) {
    String estado = switch (result.estado()) {
      case "reject" -> "No asegurable";
      case "warning" -> "Asegurable con condiciones";
      default -> "Asegurable";
    };
    return "Resultado: " + estado + ".\\n"
        + "Precio total estimado: " + result.precioTotal() + " €.";
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
      Eres un asistente experto en seguros.
      Recibirás un JSON con datos del cliente, resultado de validación y resultado de precio.
      Tu tarea es resumir, explicar y recomendar sin recalcular precios.
      Devuelve una respuesta en español, clara, orientada a cerrar la propuesta.
      """;
  }
}
