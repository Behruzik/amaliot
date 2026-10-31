package com.example.amaliyot.web;

import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.PdfReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "PDF: результаты и сертификаты", description = "Открытие или скачивание PDF текущего пользователя")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Готовый PDF", content = @Content(
                mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ApiDtos.ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Документ отсутствует или принадлежит другому пользователю", content = @Content(
                mediaType = "application/json", schema = @Schema(implementation = ApiDtos.ApiError.class)))
})
public class ResultPdfController {
    private final PdfReportService reports;

    public ResultPdfController(PdfReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Результат теста в PDF",
            description = "Сначала завершите попытку. Укажите её attemptId. "
                    + "В Swagger нажмите Execute, затем Download file. "
                    + "В клиенте передавайте Authorization: Bearer <token>; "
                    + "обычная адресная строка браузера JWT не отправляет.")
    @ApiResponse(responseCode = "409", description = "Попытка ещё не завершена", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = ApiDtos.ApiError.class)))
    @GetMapping(value = "/api/tests/results/{attemptId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> result(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable Long attemptId,
                                          @Parameter(description = "true - скачать; false - открыть в PDF-просмотрщике")
                                          @RequestParam(defaultValue = "false") boolean download) {
        byte[] pdf = reports.result(principal.userId(), attemptId);
        return response(pdf, "result-" + attemptId + ".pdf", download);
    }

    @Operation(summary = "Сертификат в PDF",
            description = "Номер certificateNumber возьмите из GET /api/certificates. "
                    + "Отозванный сертификат явно помечается в документе.")
    // The generated number always starts with GEA-. The constraint also prevents
    // ambiguity with the public /api/certificates/verify/{certificateNumber} route.
    @GetMapping(value = "/api/certificates/{certificateNumber:GEA-[A-Za-z0-9-]+}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> certificate(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String certificateNumber,
                                               @Parameter(description = "true - скачать; false - открыть в PDF-просмотрщике")
                                               @RequestParam(defaultValue = "false") boolean download) {
        byte[] pdf = reports.certificate(principal.userId(), certificateNumber);
        String safeNumber = certificateNumber.replaceAll("[^A-Za-z0-9_-]", "_");
        return response(pdf, "certificate-" + safeNumber + ".pdf", download);
    }

    private ResponseEntity<byte[]> response(byte[] pdf, String filename, boolean download) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder(download ? "attachment" : "inline").filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }
}
