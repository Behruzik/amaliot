package com.example.amaliyot.service;

import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.web.ApiDtos.CertificateView;
import com.example.amaliyot.web.ApiDtos.ResultView;
import com.example.amaliyot.web.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Generates private, Unicode PDF reports entirely in memory. */
@Service
public class PdfReportService {
    private static final Color INK = new Color(24, 42, 61);
    private static final Color MUTED = new Color(87, 104, 118);
    private static final Color BLUE = new Color(24, 93, 145);
    private static final Color PALE_BLUE = new Color(235, 244, 250);
    private static final Color RED = new Color(164, 44, 44);

    private final AttemptService attempts;
    private final UserAccountRepository users;
    private final CertificateService certificates;
    private final ZoneId zone;
    private final DateTimeFormatter dates;

    public PdfReportService(AttemptService attempts, UserAccountRepository users,
                            CertificateService certificates,
                            @Value("${app.reports.zone-id:Asia/Tashkent}") String zoneId) {
        this.attempts = attempts;
        this.users = users;
        this.certificates = certificates;
        this.zone = ZoneId.of(zoneId);
        this.dates = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.ROOT).withZone(zone);
    }

    public byte[] result(Long userId, Long attemptId) {
        // This owner-scoped lookup also rejects attempts which are not finished.
        ResultView result = attempts.myResult(userId, attemptId);
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Результат тестирования / Test natijasi #" + result.id());
            document.getDocumentInformation().setAuthor("GEADEZIST");
            try (Layout layout = new Layout(document)) {
                layout.heading("Результат тестирования");
                layout.paragraph("Test natijasi  |  Попытка № " + result.id(), 10, MUTED, 17);
                layout.field("Участник / Ishtirokchi", holderName(user));
                layout.field("Направление / Yo‘nalish", result.categoryName());
                layout.score(result.score().toPlainString() + " %",
                        "Правильных ответов: " + result.correctAnswers() + " из " + result.totalQuestions());
                layout.field("Статус результата", statusLabel(result.status()));
                layout.paragraph("Начало: " + date(result.startedAt()), 11, INK, 3);
                layout.paragraph("Завершение: " + date(result.completedAt()), 11, INK, 3);
                layout.paragraph("Продолжительность: " + duration(result.elapsedSeconds()), 11, INK, 12);
                layout.paragraph("Время указано в часовом поясе " + zone.getId() + ".", 9, MUTED, 15);
                if (result.categoryResults() != null && result.categoryResults().size() > 1) {
                    layout.section("Оценка по категориям");
                    for (var category : result.categoryResults()) {
                        layout.paragraph("Категория № " + category.categoryId() + ": "
                                + category.correctAnswers() + " из " + category.totalQuestions(), 10, INK, 3);
                    }
                    layout.space(10);
                }
                layout.paragraph("Порог выдачи сертификата: " + CertificateService.PASSING_CORRECT_ANSWERS
                        + " правильных ответов из " + CertificateService.EXAM_QUESTION_COUNT
                        + ". Сертификаты доступны в разделе «Мои сертификаты».", 9, MUTED, 9);
                layout.paragraph("Документ содержит результат на момент выгрузки. Статус сертификата проверяется отдельно по его номеру.",
                        9, MUTED, 0);
            }
            return bytes(document);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate result PDF", exception);
        }
    }

    public byte[] certificate(Long userId, String certificateNumber) {
        // The public verification method must never be used to authorize a download.
        CertificateView certificate = certificates.myCertificate(userId, certificateNumber);
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Сертификат / Sertifikat " + certificate.certificateNumber());
            document.getDocumentInformation().setAuthor("GEADEZIST");
            try (Layout layout = new Layout(document)) {
                layout.heading("Сертификат");
                layout.paragraph("Sertifikat / Certificate", 11, MUTED, 19);
                layout.section(certificate.valid() ? "ДЕЙСТВИТЕЛЕН / AMALDA" : "ОТОЗВАН / BEKOR QILINGAN",
                        certificate.valid() ? BLUE : RED);
                layout.paragraph("№ " + certificate.certificateNumber(), 10, MUTED, 18);
                layout.field("Владелец / Egasi", certificate.holderName());
                layout.field("Направление / Yo‘nalish", certificate.direction());
                layout.score(certificate.score().toPlainString() + " %",
                        "Правильных ответов: " + certificate.correctAnswers() + " из " + certificate.totalQuestions());
                layout.field("Дата выдачи (" + zone.getId() + ")", date(certificate.issuedAt()));
                layout.section("Проверка актуального статуса");
                layout.paragraph("Проверить номер можно через публичный API платформы:", 10, INK, 6);
                layout.paragraph("GET /api/certificates/verify/" + certificate.certificateNumber(), 9, BLUE, 19);
                layout.paragraph("Это электронная копия записи платформы. Актуальный статус определяется сервисом проверки сертификатов.",
                        9, MUTED, 0);
            }
            return bytes(document);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate certificate PDF", exception);
        }
    }

    private String date(Instant value) {
        return value == null ? "-" : dates.format(value);
    }

    private static String duration(long seconds) {
        long positive = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d ч %02d мин %02d с", positive / 3600, positive / 60 % 60, positive % 60);
    }

    private static String holderName(UserAccount user) {
        String name = Stream.of(user.getFirstName(), user.getLastName()).filter(Objects::nonNull)
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.joining(" "));
        if (!name.isEmpty()) return name;
        return user.getNickname() == null || user.getNickname().isBlank() ? "Участник № " + user.getId() : user.getNickname();
    }

    private static String statusLabel(AttemptStatus status) {
        return switch (status) {
            case IN_PROGRESS -> "В процессе / Jarayonda";
            case PENDING -> "Завершён, ожидает проверки / Tekshiruv kutilmoqda";
            case APPROVED -> "Подтверждён / Tasdiqlangan";
            case CANCELLED -> "Отменён / Bekor qilingan";
            case EXPIRED -> "Время истекло / Vaqt tugagan";
        };
    }

    private static byte[] bytes(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    /** Small page layout with measured word wrapping and embedded fonts. */
    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private static final float BOTTOM = 64;
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private float y;

        private Layout(PDDocument document) throws IOException {
            this.document = document;
            this.regular = loadFont(document, "/fonts/DejaVuSans.ttf");
            this.bold = loadFont(document, "/fonts/DejaVuSans-Bold.ttf");
            newPage();
        }

        private static PDType0Font loadFont(PDDocument document, String resource) throws IOException {
            try (InputStream input = PdfReportService.class.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing bundled PDF font: " + resource);
                return PDType0Font.load(document, input, true);
            }
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            text("GEADEZIST", bold, 12, BLUE, MARGIN, 791);
            text("Тесты и профессиональные навыки", regular, 9, MUTED, 303, 793);
            stream.setStrokingColor(new Color(211, 225, 235));
            stream.setLineWidth(0.7f);
            stream.moveTo(MARGIN, 775);
            stream.lineTo(MARGIN + WIDTH, 775);
            stream.stroke();
            y = 742;
        }

        private void ensureRoom(float height) throws IOException {
            if (y - height < BOTTOM) newPage();
        }

        private void heading(String value) throws IOException {
            paragraph(value, bold, 24, INK, 7);
        }

        private void section(String value) throws IOException {
            section(value, INK);
        }

        private void section(String value, Color color) throws IOException {
            ensureRoom(52);
            paragraph(value, bold, 12, color, 9);
        }

        private void field(String label, String value) throws IOException {
            ensureRoom(55);
            paragraph(label, 9, MUTED, 1);
            paragraph(value, 12, INK, 12);
        }

        private void score(String score, String detail) throws IOException {
            ensureRoom(107);
            stream.setNonStrokingColor(PALE_BLUE);
            stream.addRect(MARGIN, y - 75, WIDTH, 88);
            stream.fill();
            text(score, bold, 29, BLUE, MARGIN + 17, y - 25);
            text(detail, regular, 11, INK, MARGIN + 18, y - 51);
            y -= 101;
        }

        private void space(float points) {
            y -= points;
        }

        private void paragraph(String value, float size, Color color, float gap) throws IOException {
            paragraph(value, regular, size, color, gap);
        }

        private void paragraph(String value, PDType0Font font, float size, Color color, float gap) throws IOException {
            for (String line : wrap(value, font, size)) {
                ensureRoom(size * 1.4f);
                text(line, font, size, color, MARGIN, y);
                y -= size * 1.4f;
            }
            y -= gap;
        }

        private void text(String value, PDType0Font font, float size, Color color, float x, float baseline) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }

        private List<String> wrap(String value, PDType0Font font, float size) throws IOException {
            String safe = printable(value, font);
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : safe.split(" +")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, font, size) <= WIDTH) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                // Split long URLs/unbroken names at Unicode code point boundaries.
                for (int codePoint : word.codePoints().toArray()) {
                    String character = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && width(line + character, font, size) > WIDTH) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(character);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            if (lines.isEmpty()) lines.add("-");
            return lines;
        }

        private static float width(String text, PDType0Font font, float size) throws IOException {
            return font.getStringWidth(text) * size / 1000;
        }

        private static String printable(String value, PDType0Font font) throws IOException {
            StringBuilder safe = new StringBuilder();
            for (int codePoint : Objects.toString(value, "-").codePoints().toArray()) {
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    safe.append(' ');
                } else if (!Character.isISOControl(codePoint) && Character.getType(codePoint) != Character.FORMAT) {
                    String glyph = new String(Character.toChars(codePoint));
                    try {
                        font.encode(glyph);
                        safe.append(glyph);
                    } catch (IllegalArgumentException unsupportedGlyph) {
                        // A user-entered emoji must not break the entire PDF download.
                        safe.append('?');
                    }
                }
            }
            return safe.toString().strip();
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
            int pageCount = document.getNumberOfPages();
            for (int index = 0; index < pageCount; index++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(index),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset(MARGIN, 34);
                    footer.showText("GEADEZIST  |  Страница " + (index + 1) + " / " + pageCount);
                    footer.endText();
                }
            }
        }
    }
}
