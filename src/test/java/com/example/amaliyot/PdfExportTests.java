package com.example.amaliyot;

import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.service.CertificateService;
import com.example.amaliyot.service.PdfReportService;
import com.example.amaliyot.web.ApiDtos.CategoryScore;
import com.example.amaliyot.web.ApiDtos.CertificateView;
import com.example.amaliyot.web.ApiDtos.ResultView;
import com.example.amaliyot.web.ApiException;
import com.example.amaliyot.web.ResultPdfController;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdfExportTests {
    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 42L;
    private static final String CERTIFICATE_NUMBER = "GEA-00000000-0000-4000-8000-000000000042";
    private final AttemptService attempts = mock(AttemptService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final CertificateService certificates = mock(CertificateService.class);
    private PdfReportService reports;

    @BeforeEach
    void setUp() {
        reports = new PdfReportService(attempts, users, certificates, "Asia/Tashkent");
    }

    @Test
    void resultPdfEmbedsUnicodeFontsAndContainsScoreAndDates() throws Exception {
        givenResult("Геодезия / O‘lchash ishlari", List.of(new CategoryScore(1L, 35, 40)));
        byte[] bytes = reports.result(USER_ID, ATTEMPT_ID);
        assertEquals("%PDF-", new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Результат тестирования"));
            assertTrue(text.contains("Беҳруз Ғуломов"));
            assertTrue(text.contains("O‘lchash ishlari"));
            assertTrue(text.contains("87.50 %"));
            assertTrue(text.contains("Правильных ответов: 35 из 40"));
            assertTrue(text.contains("30.10.2026 14:00:00"));
            assertTrue(text.contains("Asia/Tashkent"));
            for (var fontName : pdf.getPage(0).getResources().getFontNames()) {
                assertTrue(pdf.getPage(0).getResources().getFont(fontName).isEmbedded());
            }
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/sample-result.pdf"), bytes);
    }

    @Test
    void longTextIsWrappedAndMultiplePagesAreNumbered() throws Exception {
        List<CategoryScore> categories = IntStream.rangeClosed(1, 65)
                .mapToObj(index -> new CategoryScore((long) index, 1, 2)).toList();
        givenResult("ОченьДлинноеНазваниеБезПробелов".repeat(30) + "\n\t🧪", categories);
        try (PDDocument pdf = Loader.loadPDF(reports.result(USER_ID, ATTEMPT_ID))) {
            assertTrue(pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Категория № 65: 1 из 2"));
            assertTrue(text.contains("Страница " + pdf.getNumberOfPages() + " / " + pdf.getNumberOfPages()));
            assertTrue(text.contains("?"));
        }
    }

    @Test
    void ownerLookupRunsBeforeAnyProfileReadOrRendering() {
        when(attempts.myResult(99L, ATTEMPT_ID))
                .thenThrow(ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt was not found"));
        ApiException hidden = assertThrows(ApiException.class, () -> reports.result(99L, ATTEMPT_ID));
        assertEquals(404, hidden.getStatus().value());
        verifyNoInteractions(users, certificates);
    }

    @Test
    void unfinishedAttemptPreservesConflict() {
        when(attempts.myResult(USER_ID, ATTEMPT_ID))
                .thenThrow(ApiException.conflict("ATTEMPT_NOT_FINISHED", "This attempt has not been finished"));
        ApiException conflict = assertThrows(ApiException.class, () -> reports.result(USER_ID, ATTEMPT_ID));
        assertEquals(409, conflict.getStatus().value());
        verifyNoInteractions(users, certificates);
    }

    @Test
    void validAndRevokedCertificatesHaveUnambiguousStatus() throws Exception {
        for (boolean valid : new boolean[]{true, false}) {
            when(certificates.myCertificate(USER_ID, CERTIFICATE_NUMBER)).thenReturn(new CertificateView(
                    CERTIFICATE_NUMBER, "Беҳруз Ғуломов", "Геодезия / O‘lchash ishlari",
                    35, 40, new BigDecimal("87.50"), Instant.parse("2026-10-30T09:30:00Z"), valid));
            byte[] bytes = reports.certificate(USER_ID, CERTIFICATE_NUMBER);
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                assertEquals(1, pdf.getNumberOfPages());
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains(CERTIFICATE_NUMBER));
                assertTrue(text.contains(valid ? "ДЕЙСТВИТЕЛЕН" : "ОТОЗВАН"));
                assertFalse(text.contains(valid ? "ОТОЗВАН" : "ДЕЙСТВИТЕЛЕН"));
            }
            if (valid) {
                Files.createDirectories(Path.of("target"));
                Files.write(Path.of("target/sample-certificate.pdf"), bytes);
            }
        }
        verify(certificates, never()).verify(anyString());
    }

    @Test
    void controllerUsesPdfHeadersAndSupportsAttachment() {
        PdfReportService mockReports = mock(PdfReportService.class);
        byte[] bytes = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(mockReports.result(USER_ID, ATTEMPT_ID)).thenReturn(bytes);
        ResultPdfController controller = new ResultPdfController(mockReports);
        AuthPrincipal principal = new AuthPrincipal(USER_ID, Role.USER);
        for (boolean download : new boolean[]{false, true}) {
            var response = controller.result(principal, ATTEMPT_ID, download);
            assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
            assertEquals(bytes.length, response.getHeaders().getContentLength());
            assertEquals(download ? "attachment" : "inline", response.getHeaders().getContentDisposition().getType());
            assertEquals("result-42.pdf", response.getHeaders().getContentDisposition().getFilename());
            assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
            assertArrayEquals(bytes, response.getBody());
        }
    }

    private void givenResult(String category, List<CategoryScore> scores) {
        UserAccount user = new UserAccount();
        user.setId(USER_ID);
        user.setFirstName("Беҳруз");
        user.setLastName("Ғуломов");
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(attempts.myResult(USER_ID, ATTEMPT_ID)).thenReturn(new ResultView(
                ATTEMPT_ID, USER_ID, category, AttemptStatus.PENDING,
                Instant.parse("2026-10-30T09:00:00Z"), Instant.parse("2026-10-30T09:27:14Z"),
                1634, 35, 40, new BigDecimal("87.50"), scores));
    }
}
