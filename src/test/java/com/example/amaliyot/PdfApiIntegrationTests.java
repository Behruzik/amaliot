package com.example.amaliyot;

import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.Category;
import com.example.amaliyot.domain.Entities.Certificate;
import com.example.amaliyot.domain.Entities.TestAttempt;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.repository.CertificateRepository;
import com.example.amaliyot.repository.TestAttemptRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PdfApiIntegrationTests {
    private static final AtomicLong PHONE = new AtomicLong(998949100000L);
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired UserAccountRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TestAttemptRepository attempts;
    @Autowired CertificateRepository certificates;
    @Autowired JwtService jwt;
    @Autowired TransactionTemplate transactions;

    @Test
    void pdfRequiresLoginAndHidesOtherUsersResults() throws Exception {
        Fixture owner = fixture(true, true);
        Fixture other = fixture(true, false);
        String path = "/api/tests/results/" + owner.attemptId() + "/pdf";
        assertEquals(401, get(path, null).statusCode());
        HttpResponse<byte[]> hidden = get(path, other.token());
        assertEquals(404, hidden.statusCode());
        assertTrue(hidden.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        HttpResponse<byte[]> response = get(path, owner.token());
        assertEquals(200, response.statusCode());
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").startsWith("inline"));
        assertEquals("%PDF-", new String(response.body(), 0, 5, StandardCharsets.US_ASCII));
        assertTrue(get(path + "?download=true", owner.token()).headers()
                .firstValue("Content-Disposition").orElse("").startsWith("attachment"));

        String certificatePath = "/api/certificates/" + owner.certificateNumber() + "/pdf";
        assertEquals(401, get(certificatePath, null).statusCode());
        assertEquals(404, get(certificatePath, other.token()).statusCode());
        assertEquals(200, get(certificatePath, owner.token()).statusCode());
    }

    @Test
    void unfinishedResultReturnsJsonConflictEvenWhenPdfIsAccepted() throws Exception {
        Fixture active = fixture(false, false);
        HttpResponse<byte[]> response = get("/api/tests/results/" + active.attemptId() + "/pdf", active.token());
        assertEquals(409, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(new String(response.body(), StandardCharsets.UTF_8).contains("ATTEMPT_NOT_FINISHED"));
    }

    private HttpResponse<byte[]> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/pdf").GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private Fixture fixture(boolean completed, boolean withCertificate) {
        return transactions.execute(status -> {
            String unique = UUID.randomUUID().toString();
            UserAccount user = users.saveAndFlush(new UserAccount("+" + PHONE.incrementAndGet(),
                    "test-only-password-hash-" + unique, "Алишер", "Qodirov", true));
            Category category = new Category();
            category.setName("PDF test " + unique);
            category = categories.saveAndFlush(category);
            TestAttempt attempt = new TestAttempt();
            attempt.setUser(user);
            attempt.setCategory(category);
            attempt.setStartedAt(Instant.now().minusSeconds(60));
            attempt.setDeadlineAt(Instant.now().plusSeconds(3600));
            attempt.setStatus(completed ? AttemptStatus.PENDING : AttemptStatus.IN_PROGRESS);
            attempt.setCompletedAt(completed ? Instant.now() : null);
            attempt.setTotalQuestions(40);
            attempt.setCorrectAnswers(completed ? 35 : 0);
            attempt.setScore(completed ? new BigDecimal("87.50") : BigDecimal.ZERO);
            attempt = attempts.saveAndFlush(attempt);
            String number = "GEA-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
            if (withCertificate) {
                Certificate certificate = new Certificate();
                certificate.setCertificateNumber(number);
                certificate.setUser(user);
                certificate.setCategory(category);
                certificate.setAttempt(attempt);
                certificate.setHolderName("Алишер Qodirov");
                certificate.setDirection(category.getName());
                certificate.setCorrectAnswers(35);
                certificate.setTotalQuestions(40);
                certificate.setScore(new BigDecimal("87.50"));
                certificates.saveAndFlush(certificate);
            }
            return new Fixture(attempt.getId(), jwt.issue(user).value(), number);
        });
    }

    private record Fixture(Long attemptId, String token, String certificateNumber) {}
}
