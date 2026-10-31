package com.example.amaliyot.web;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.ContentType;
import com.example.amaliyot.domain.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class ApiDtos {
    private ApiDtos() {}

    @Schema(description = "Новый пользователь. Email и телефон должны быть уникальны; разрешён email @gmail.com.")
    public record RegisterRequest(@NotBlank @Email @Size(max = 254) @Pattern(regexp = "(?i)^[^\\s@]+@gmail\\.com$")
                                  @Schema(example = "new.student@gmail.com") String email,
                                  @NotBlank @Size(max = 32) @Pattern(regexp = "^\\+?[0-9 ()\\-]{7,24}$")
                                  @Schema(description = "Ваш уникальный телефон в международном формате", example = "+998900001234") String phone,
                                  @NotBlank @Size(min = 6, max = 72)
                                  @Schema(format = "password", example = "NewUserPass123!") String password,
                                  @Size(max = 100) @Schema(example = "Алишер") String firstName,
                                  @Size(max = 100) @Schema(example = "Каримов") String lastName,
                                  @Size(max = 50) @Schema(example = "new-student") String nickname,
                                  @Schema(description = "Для регистрации нужно принять оферту", example = "true") boolean offerAccepted,
                                  @Pattern(regexp = "^(|FEMALE|MALE|OTHER)$") String gender,
                                  @Size(max = 72) @Schema(format = "password", example = "NewUserPass123!") String confirmPassword) {
        public RegisterRequest(String email, String password, String firstName, String lastName, boolean offerAccepted) {
            this(email, null, password, firstName, lastName, null, offerAccepted, null, null);
        }
    }
    public record LoginRequest(@JsonAlias("phone") @NotBlank @Size(max = 254)
                               @Schema(description = "Email аккаунта; для старых аккаунтов допускается телефон. Пример работает в профиле demo.",
                                       example = "student.demo@gmail.com") String email,
                               @NotBlank @Size(max = 72) @Schema(format = "password", example = "DemoUser123!") String password) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 6, max = 72) String newPassword) {}
    public record ForgotPasswordRequest(@NotBlank String phone) {}
    public record AuthResponse(@Schema(description = "Скопируйте только это значение в Authorize без префикса Bearer") String accessToken,
                               @Schema(example = "Bearer") String tokenType, Instant expiresAt, UserView user) {}
    public record UserView(Long id, String phone, String email, String nickname, String firstName, String lastName, Role role,
                           String gender, boolean offerAccepted, LocalDate birthDate, String region,
                           String district, String address, String avatarUrl, boolean active,
                           @Schema(description = "ID выбранной профессии; передаётся как categoryId при запуске теста") Long professionId,
                           String professionName) {}
    public record UserDashboardView(UserView user, Instant createdAt, long activeDurationSeconds,
                                    long totalExams, long passedExams, long failedExams,
                                    List<String> workDirections, List<CertificateView> certificates) {}
    @Schema(description = "Частичное обновление профиля: пропускайте поля, которые менять не нужно.")
    public record ProfileUpdateRequest(@Size(max = 100) @Schema(example = "Алишер") String firstName,
                                       @Size(max = 100) @Schema(example = "Каримов") String lastName,
                                       @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{7,24}$") String phone,
                                       @Size(max = 50) String nickname,
                                       @Pattern(regexp = "^(|FEMALE|MALE|OTHER)$") String gender,
                                       @PastOrPresent @Schema(example = "2000-05-15") LocalDate birthDate,
                                       @Size(max = 120) @Schema(description = "name из GET /api/locations/regions") String region,
                                       @Size(max = 120) @Schema(description = "name района выбранного региона из /api/locations") String district,
                                       @Size(max = 500) @Schema(example = "Ташкент, учебный адрес") String address,
                                       @Schema(example = "true") Boolean offerAccepted,
                                       @Positive @Schema(description = "id активной профессии из GET /api/professions") Long professionId) {}

    public record CategoryRequest(@NotBlank @Size(max = 160) String name,
                                  @Size(max = 2000) String description,
                                  @Size(max = 500) String imageUrl,
                                  boolean active,
                                  @Min(1) @Max(600) int durationMinutes,
                                  @Min(40) @Max(40) int questionCount,
                                  @Min(0) @Max(0) int additionalQuestionCount,
                                  @Min(0) @Max(8760) int retakeDelayHours) {}
    public record CategoryView(Long id, String name, String description, String imageUrl, boolean active,
                               int durationMinutes, int questionCount, int additionalQuestionCount,
                               int retakeDelayHours) {}
    public record OptionInput(@Size(max = 2000) String label, @Size(max = 500) String imageUrl, boolean correct) {}
    public record QuestionRequest(@NotNull Long categoryId, @NotBlank @Size(max = 5000) String body,
                                  @Size(max = 500) String imageUrl, @NotBlank @Size(max = 16) String difficulty,
                                  @NotNull AnswerType answerType, @Digits(integer = 14, fraction = 6) BigDecimal numericAnswer,
                                  @DecimalMin("0") @Digits(integer = 14, fraction = 6) BigDecimal numericTolerance,
                                  @Size(max = 20) List<@NotNull @Valid OptionInput> options) {}
    public record QuestionView(Long id, Long categoryId, String body, String imageUrl, String difficulty,
                               AnswerType answerType, BigDecimal numericAnswer, BigDecimal numericTolerance,
                               boolean active, List<OptionView> options, Long authorId, Long editorId) {}
    public record OptionView(Long id, String label, String imageUrl, Boolean correct) {}

    @Schema(description = "SINGLE/MULTIPLE: ID вариантов из текущей попытки. NUMERIC: только numericValue. Пустой ответ очищает выбор.")
    public record AnswerRequest(@Size(max = 20)
                                @Schema(description = "questions[].options[].id текущей попытки. Для SINGLE максимум один ID; [] очищает выбор.")
                                Set<@NotNull Long> selectedOptionIds,
                                @Digits(integer = 14, fraction = 6)
                                @Schema(description = "Только для NUMERIC: до 14 целых цифр и 6 знаков после точки", example = "12.5") BigDecimal numericValue) {}
    public record TestOptionView(Long id, String label, String imageUrl) {}
    public record TestQuestionView(Long id, int order, Long categoryId, String body, String imageUrl,
                                   AnswerType answerType, List<TestOptionView> options,
                                   Set<Long> selectedOptionIds, BigDecimal numericValue) {}
    public record AttemptView(Long id, Long categoryId, String categoryName, AttemptStatus status,
                              Instant startedAt, Instant deadlineAt, Instant completedAt, Instant savedAt,
                              int totalQuestions, int correctAnswers, BigDecimal score,
                              List<TestQuestionView> questions) {}
    public record SavedAttemptView(Long id, Long categoryId, String categoryName, AttemptStatus status,
                                   Instant startedAt, Instant deadlineAt, Instant savedAt,
                                   int totalQuestions, int answeredQuestions) {}
    public record RecommendedTestView(Long categoryId, String profession, String description,
                                      int activeQuestionCount, int requiredQuestionCount, boolean ready) {}
    public record RecommendationView(Long professionId, String professionName,
                                     List<RecommendedTestView> tests) {}
    public record CategoryScore(Long categoryId, int correctAnswers, int totalQuestions) {}
    public record ResultView(@Schema(description = "ID попытки, используемый в /api/tests/results/{attemptId}/pdf") Long id,
                             Long userId, String categoryName,
                             @Schema(description = "PENDING - завершён и ожидает проверки; APPROVED - подтверждён; CANCELLED - отменён; EXPIRED - истекло время") AttemptStatus status,
                             Instant startedAt, Instant completedAt, long elapsedSeconds,
                             int correctAnswers, @Schema(example = "40") int totalQuestions,
                             @Schema(description = "Процент правильных ответов, рассчитанный сервером", example = "95.00") BigDecimal score,
                             List<CategoryScore> categoryResults) {}
    public record AdminQuestionResult(Long questionId, int order, Long categoryId, String body, AnswerType answerType,
                                      List<OptionView> options, Set<Long> selectedOptionIds,
                                      BigDecimal expectedNumericValue, BigDecimal submittedNumericValue,
                                      boolean correct) {}
    public record AdminAttemptView(ResultView result, String userPhone, String userEmail, String userNickname,
                                   String userName, List<AdminQuestionResult> questions, Instant archivedAt) {}
    public record AdminUserRequest(@NotBlank @Email @Size(max = 254) @Pattern(regexp = "(?i)^[^\\s@]+@gmail\\.com$") String email,
                                  @NotBlank @Size(max = 32) @Pattern(regexp = "^\\+?[0-9 ()\\-]{7,24}$") String phone,
                                  @NotBlank @Size(min = 6, max = 72) String password,
                                  @Size(max = 50) String nickname,
                                  @Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                  @NotNull Role role, boolean active) {}
    public record AdminUserUpdateRequest(@Size(max = 100) String firstName, @Size(max = 100) String lastName,
                                         @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{7,24}$") String phone,
                                         @Size(max = 50) String nickname,
                                         Role role, Boolean active) {}
    public record CertificateView(@Schema(description = "Номер для публичной проверки и /api/certificates/{certificateNumber}/pdf") String certificateNumber,
                                  String holderName, String direction,
                                  int correctAnswers, int totalQuestions, java.math.BigDecimal score,
                                  java.time.Instant issuedAt, boolean valid) {}
    public record SupportRequestCreate(@NotBlank @Pattern(regexp = "PHONE_CALL|APPLICATION|FEEDBACK") String type,
                                       @NotBlank @Size(max = 200) String subject,
                                       @NotBlank @Size(max = 5000) String message,
                                       @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{7,24}$") String phone) {}
    public record SupportRequestUpdate(@NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|CLOSED") String status,
                                       @Size(max = 3000) String staffReply) {}
    public record SupportRequestView(Long id, Long userId, String nickname, String email, String phone,
                                     String type, String subject, String message, String status,
                                     String staffReply, Instant createdAt, Instant updatedAt) {}
    public record SupportContactView(String phone, List<String> methods) {}
    public record UserStatusRequest(boolean active) {}
    public record GrantRetakeRequest(@NotNull Long userId, @NotNull Long categoryId) {}
    public record DashboardView(long totalUsers, long activeUsers, long activeCategories,
                                long totalQuestions, long pendingAttempts, long approvedAttempts,
                                long cancelledAttempts, long expiredAttempts) {}
    public record RegionView(Long id, String name, boolean active) {}
    public record RegionRequest(@NotBlank @Size(max = 120) String name, Boolean active) {}
    public record DistrictView(Long id, Long regionId, String regionName, String name, boolean active) {}
    public record DistrictRequest(@NotNull Long regionId, @NotBlank @Size(max = 120) String name, Boolean active) {}
    public record ManagedContentView(ContentType type, String content, Instant updatedAt) {}
    public record ManagedContentRequest(@NotBlank @Size(max = 20000) String content) {}
    public record MessageView(String message) {}
    @Schema(description = "Единый JSON-ответ при ошибке, в том числе при запросе PDF")
    public record ApiError(@Schema(example = "ATTEMPT_NOT_FINISHED") String code,
                           @Schema(example = "This attempt has not been finished") String message,
                           Instant timestamp, String path) {}
}
