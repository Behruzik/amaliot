package com.example.amaliyot.service;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.*;
import com.example.amaliyot.repository.*;
import com.example.amaliyot.web.ApiDtos.*;
import com.example.amaliyot.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AttemptService {
    private final UserAccountRepository users;
    private final CategoryRepository categories;
    private final QuestionRepository questions;
    private final TestAttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final RetakeGrantRepository retakeGrants;
    private final CertificateService certificates;

    public AttemptService(UserAccountRepository users, CategoryRepository categories, QuestionRepository questions,
                         TestAttemptRepository attempts, AttemptAnswerRepository answers, RetakeGrantRepository retakeGrants,
                         CertificateService certificates) {
        this.users = users; this.categories = categories; this.questions = questions;
        this.attempts = attempts; this.answers = answers; this.retakeGrants = retakeGrants;
        this.certificates = certificates;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AttemptView start(Long userId, Long categoryId) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        Category category = categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category was not found"));
        if (!category.isActive()) throw ApiException.conflict("CATEGORY_INACTIVE", "This category is not accepting attempts");
        if (user.getFirstName() == null || user.getFirstName().isBlank() || user.getLastName() == null || user.getLastName().isBlank()
                || user.getBirthDate() == null || user.getRegion() == null || user.getRegion().isBlank()
                || user.getDistrict() == null || user.getDistrict().isBlank() || user.getAddress() == null || user.getAddress().isBlank()
                || !user.isOfferAccepted()) {
            throw ApiException.conflict("PROFILE_INCOMPLETE", "Complete your required profile fields before starting a test");
        }
        for (TestAttempt active : attempts.findByUserIdAndCategoryIdAndStatus(userId, categoryId, AttemptStatus.IN_PROGRESS)) {
            expireIfNeeded(active);
            if (active.getStatus() == AttemptStatus.IN_PROGRESS) {
                throw ApiException.conflict("ATTEMPT_IN_PROGRESS", "Finish the active attempt before starting another");
            }
        }
        Optional<RetakeGrant> grant = retakeGrants.findFirstByUserIdAndCategoryIdAndConsumedFalseOrderByCreatedAtAsc(userId, categoryId);
        Optional<TestAttempt> previous = attempts.findFirstByUserIdAndCategoryIdOrderByCreatedAtDesc(userId, categoryId);
        if (previous.isPresent() && previous.get().getCompletedAt() != null
                && previous.get().getStatus() != AttemptStatus.CANCELLED && grant.isEmpty()) {
            Instant allowedAt = previous.get().getCompletedAt().plusSeconds((long) category.getRetakeDelayHours() * 3600);
            if (Instant.now().isBefore(allowedAt)) {
                throw ApiException.conflict("RETAKE_NOT_ALLOWED", "The category retake period has not elapsed");
            }
        }

        List<Question> selected = selectQuestions(category, userId);
        // Both supported databases persist timestamps at microsecond precision.
        // Match that precision before returning DTOs so a later GET is identical.
        Instant started = Instant.now().truncatedTo(ChronoUnit.MICROS);
        TestAttempt attempt = new TestAttempt();
        attempt.setUser(user); attempt.setCategory(category); attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(started); attempt.setDeadlineAt(started.plusSeconds((long) category.getDurationMinutes() * 60));
        attempt.setTotalQuestions(selected.size());
        int order = 1;
        for (Question source : selected) {
            AttemptQuestion snapshot = new AttemptQuestion();
            snapshot.setAttempt(attempt); snapshot.setSourceQuestionId(source.getId());
            snapshot.setSourceCategoryId(category.getId()); snapshot.setBody(source.getBody());
            snapshot.setImageUrl(source.getImageUrl()); snapshot.setAnswerType(source.getAnswerType());
            snapshot.setNumericAnswer(source.getNumericAnswer()); snapshot.setNumericTolerance(
                    source.getNumericTolerance() == null ? BigDecimal.ZERO : source.getNumericTolerance());
            snapshot.setDisplayOrder(order++);
            for (AnswerOption option : source.getOptions()) {
                AttemptOption copy = new AttemptOption(); copy.setAttemptQuestion(snapshot);
                copy.setLabel(option.getLabel()); copy.setImageUrl(option.getImageUrl()); copy.setCorrect(option.isCorrect());
                snapshot.getOptions().add(copy);
            }
            attempt.getQuestions().add(snapshot);
        }
        attempts.save(attempt);
        grant.ifPresent(value -> value.setConsumed(true));
        return toAttemptView(attempt);
    }

    private List<Question> selectQuestions(Category category, Long userId) {
        List<Question> pool = new ArrayList<>(questions.findByGlobalPoolTrueAndActiveTrueAndDifficulty(
                com.example.amaliyot.domain.Difficulty.EASY));
        // Keeps isolated legacy/test databases usable; a fresh non-test database is
        // populated with the shared bank by GlobalEasyQuestionSeeder at startup.
        if (pool.isEmpty()) pool.addAll(questions.findByCategoryIdAndActiveTrue(category.getId()));
        if (pool.size() < category.getQuestionCount()) {
            throw ApiException.conflict("QUESTION_BANK_INSUFFICIENT",
                    "The shared easy question bank does not contain enough active questions");
        }
        attempts.findByUserId(userId).stream().max(Comparator.comparing(TestAttempt::getCreatedAt))
                .ifPresent(previous -> attempts.findWithSnapshotById(previous.getId()).ifPresent(snapshot -> {
                    Set<Long> previousIds = snapshot.getQuestions().stream().map(AttemptQuestion::getSourceQuestionId)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    if (pool.size() - previousIds.size() >= category.getQuestionCount())
                        pool.removeIf(question -> previousIds.contains(question.getId()));
                }));
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, category.getQuestionCount()));
    }

    @Transactional
    public AttemptView get(Long userId, Long attemptId) {
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        expireIfNeeded(attempt);
        return toAttemptView(attempt);
    }

    @Transactional(readOnly = true)
    public List<SavedAttemptView> savedAttempts(Long userId) {
        Instant now = Instant.now();
        return attempts.findByUserIdAndStatusOrderByCreatedAtDesc(userId, AttemptStatus.IN_PROGRESS).stream()
                .filter(attempt -> attempt.getDeadlineAt().isAfter(now))
                .map(attempt -> new SavedAttemptView(attempt.getId(), attempt.getCategory().getId(),
                        attempt.getCategory().getName(), attempt.getStatus(), attempt.getStartedAt(),
                        attempt.getDeadlineAt(), attempt.getSavedAt(), attempt.getTotalQuestions(),
                        (int) answersFor(attempt).values().stream().filter(AttemptService::hasSubmittedValue).count()))
                .toList();
    }

    // Expiry is an authoritative state transition: the expected 409 must not
    // roll its grading/completion timestamp back when save/resume is too late.
    @Transactional(noRollbackFor = ApiException.class)
    public AttemptView saveForLater(Long userId, Long attemptId) {
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        expireIfNeeded(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            throw ApiException.conflict("ATTEMPT_NOT_ACTIVE", "Only an active attempt can be saved for later");
        attempt.setSavedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return toAttemptView(attempt);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AttemptView resume(Long userId, Long attemptId) {
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        expireIfNeeded(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            throw ApiException.conflict("ATTEMPT_NOT_ACTIVE", "This attempt can no longer be resumed");
        attempt.setSavedAt(null);
        return toAttemptView(attempt);
    }

    @Transactional
    public AttemptView answer(Long userId, Long attemptId, Long questionId, AnswerRequest request) {
        // Serialize answers with finish/expiry so a committed answer cannot be omitted from grading.
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) throw ApiException.conflict("ATTEMPT_NOT_ACTIVE", "This attempt no longer accepts answers");
        if (!Instant.now().isBefore(attempt.getDeadlineAt())) {
            finish(attempt, true);
            return toAttemptView(attempt);
        }
        AttemptQuestion question = attempt.getQuestions().stream().filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_QUESTION_NOT_FOUND", "Question was not found in this attempt"));
        Set<Long> selected = request.selectedOptionIds() == null ? Set.of() : new HashSet<>(request.selectedOptionIds());
        Set<Long> available = question.getOptions().stream().map(AttemptOption::getId).collect(Collectors.toSet());
        if (!available.containsAll(selected)) throw ApiException.badRequest("INVALID_OPTION", "An answer option does not belong to this question");
        if (question.getAnswerType() == AnswerType.NUMERIC) {
            if (!selected.isEmpty()) throw ApiException.badRequest("INVALID_ANSWER", "Numeric questions do not accept option ids");
            if (request.numericValue() != null && !fitsNumericColumn(request.numericValue()))
                throw ApiException.badRequest("INVALID_NUMERIC_ANSWER", "Numeric answer supports at most 14 integer digits and 6 decimal places");
        } else {
            if (request.numericValue() != null) throw ApiException.badRequest("INVALID_ANSWER", "Choice questions do not accept a numeric answer");
            if (question.getAnswerType() == AnswerType.SINGLE && selected.size() > 1)
                throw ApiException.badRequest("INVALID_ANSWER", "Choose at most one answer");
        }
        AttemptAnswer answer = answers.findByAttemptQuestionId(question.getId()).orElseGet(() -> {
            AttemptAnswer created = new AttemptAnswer(); created.setAttemptQuestion(question); return created;
        });
        answer.setSelectedOptionIds(selected); answer.setNumericValue(request.numericValue());
        answers.save(answer);
        return toAttemptView(attempt);
    }

    @Transactional
    public ResultView finish(Long userId, Long attemptId) {
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        if (attempt.getCompletedAt() == null) finish(attempt, !Instant.now().isBefore(attempt.getDeadlineAt()));
        return toResultView(attempt);
    }

    @Transactional(readOnly = true)
    public Page<ResultView> myResults(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "completedAt"));
        Page<TestAttempt> found = attempts.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId), cb.isNotNull(root.get("completedAt"))), pageable);
        return found.map(a -> toResultView(attempts.findWithSnapshotByIdAndUserId(a.getId(), userId).orElse(a)));
    }

    @Scheduled(fixedDelayString = "${tests.expiry-scan-ms:15000}")
    @Transactional
    public void expireDueAttempts() {
        for (TestAttempt attempt : attempts.findByStatusAndDeadlineAtLessThanEqual(AttemptStatus.IN_PROGRESS, Instant.now())) {
            if (attempt.getCompletedAt() == null) finish(attempt, true);
        }
    }

    @Transactional
    public ResultView myResult(Long userId, Long attemptId) {
        TestAttempt attempt = ownedAttemptForUpdate(userId, attemptId);
        expireIfNeeded(attempt);
        if (attempt.getCompletedAt() == null) throw ApiException.conflict("ATTEMPT_NOT_FINISHED", "This attempt has not been finished");
        return toResultView(attempt);
    }

    public ResultView resultForAdmin(TestAttempt attempt) { return toResultView(attempt); }

    private TestAttempt ownedAttemptForUpdate(Long userId, Long attemptId) {
        TestAttempt locked = attempts.findLockedByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt was not found"));
        // The row lock stays held until commit while this separate query loads
        // the immutable snapshot without locking outer-joined child tables.
        return attempts.findWithSnapshotByIdAndUserId(attemptId, userId).orElse(locked);
    }

    private void expireIfNeeded(TestAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && !Instant.now().isBefore(attempt.getDeadlineAt())) finish(attempt, true);
    }

    private void finish(TestAttempt attempt, boolean expired) {
        if (attempt.getCompletedAt() != null) return;
        Map<Long, AttemptAnswer> submitted = answersFor(attempt);
        int correct = 0;
        for (AttemptQuestion question : attempt.getQuestions()) {
            AttemptAnswer answer = submitted.get(question.getId());
            if (isCorrect(question, answer)) correct++;
        }
        int total = attempt.getQuestions().size();
        BigDecimal score = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(correct)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        attempt.setCorrectAnswers(correct); attempt.setTotalQuestions(total); attempt.setScore(score);
        attempt.setCompletedAt(expired ? attempt.getDeadlineAt() : Instant.now().truncatedTo(ChronoUnit.MICROS));
        attempt.setStatus(expired ? AttemptStatus.EXPIRED : AttemptStatus.PENDING);
        if (!expired) certificates.issueForPassedAttempt(attempt);
    }

    private boolean isCorrect(AttemptQuestion question, AttemptAnswer answer) {
        if (answer == null) return false;
        if (question.getAnswerType() == AnswerType.NUMERIC) {
            if (answer.getNumericValue() == null || question.getNumericAnswer() == null) return false;
            BigDecimal tolerance = question.getNumericTolerance() == null ? BigDecimal.ZERO : question.getNumericTolerance();
            return answer.getNumericValue().subtract(question.getNumericAnswer()).abs().compareTo(tolerance) <= 0;
        }
        Set<Long> expected = question.getOptions().stream().filter(AttemptOption::isCorrect).map(AttemptOption::getId).collect(Collectors.toSet());
        Set<Long> selected = answer.getSelectedOptionIds() == null ? Set.of() : answer.getSelectedOptionIds();
        return !expected.isEmpty() && expected.equals(selected);
    }

    private AttemptView toAttemptView(TestAttempt attempt) {
        Map<Long, AttemptAnswer> submitted = answersFor(attempt);
        List<TestQuestionView> views = attempt.getQuestions().stream()
                .sorted(Comparator.comparingInt(AttemptQuestion::getDisplayOrder))
                .map(question -> {
                    AttemptAnswer answer = submitted.get(question.getId());
                    List<TestOptionView> options = question.getOptions().stream()
                            .map(option -> new TestOptionView(option.getId(), option.getLabel(), option.getImageUrl())).toList();
                    return new TestQuestionView(question.getId(), question.getDisplayOrder(), question.getSourceCategoryId(),
                            question.getBody(), question.getImageUrl(), question.getAnswerType(), options,
                            answer == null ? Set.of() : answer.getSelectedOptionIds(), answer == null ? null : answer.getNumericValue());
                }).toList();
        return new AttemptView(attempt.getId(), attempt.getCategory().getId(), attempt.getCategory().getName(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getDeadlineAt(), attempt.getCompletedAt(), attempt.getSavedAt(), attempt.getTotalQuestions(),
                attempt.getCorrectAnswers(), attempt.getScore(), views);
    }

    private ResultView toResultView(TestAttempt attempt) {
        Map<Long, AttemptAnswer> submitted = answersFor(attempt);
        Map<Long, int[]> byCategory = new TreeMap<>();
        for (AttemptQuestion question : attempt.getQuestions()) {
            int[] counts = byCategory.computeIfAbsent(question.getSourceCategoryId(), ignored -> new int[2]);
            counts[1]++;
            if (isCorrect(question, submitted.get(question.getId()))) counts[0]++;
        }
        List<CategoryScore> categoryResults = byCategory.entrySet().stream()
                .map(entry -> new CategoryScore(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();
        long elapsed = attempt.getCompletedAt() == null ? 0 : Math.max(0, Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).getSeconds());
        return new ResultView(attempt.getId(), attempt.getUser().getId(), attempt.getCategory().getName(), attempt.getStatus(),
                attempt.getStartedAt(), attempt.getCompletedAt(), elapsed, attempt.getCorrectAnswers(),
                attempt.getTotalQuestions(), attempt.getScore(), categoryResults);
    }

    private Map<Long, AttemptAnswer> answersFor(TestAttempt attempt) {
        return answers.findByAttemptQuestionAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getAttemptQuestion().getId(), answer -> answer));
    }

    private static boolean hasSubmittedValue(AttemptAnswer answer) {
        return answer.getNumericValue() != null
                || (answer.getSelectedOptionIds() != null && !answer.getSelectedOptionIds().isEmpty());
    }

    static boolean fitsNumericColumn(BigDecimal value) {
        long integerDigits = Math.max(0L, (long) value.precision() - value.scale());
        return integerDigits <= 14 && Math.max(0, value.scale()) <= 6;
    }
}
