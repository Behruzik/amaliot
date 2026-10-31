package com.example.amaliyot;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.*;
import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.repository.QuestionRepository;
import com.example.amaliyot.repository.TestAttemptRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.service.AdminService;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.web.ApiDtos.*;
import com.example.amaliyot.web.ApiException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests use real transaction boundaries, including expected 409 responses. */
@SpringBootTest
@ActiveProfiles("test")
class DomainRegressionTests {
    private static final AtomicLong NEXT_PHONE = new AtomicLong(70_000_000);
    @Autowired UserAccountRepository users;
    @Autowired CategoryRepository categories;
    @Autowired QuestionRepository questions;
    @Autowired TestAttemptRepository repository;
    @Autowired AttemptService attempts;
    @Autowired AdminService admin;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    enum ExpiredAction { SAVE, RESUME }

    @Test
    void publicTimestampsRemainIdenticalAfterDatabaseRoundTrip() {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView started = attempts.start(seed.userId(), seed.categoryId());
        AttemptView loaded = attempts.get(seed.userId(), started.id());
        assertEquals(started.startedAt(), loaded.startedAt());
        assertEquals(started.deadlineAt(), loaded.deadlineAt());
        assertEquals(0, started.startedAt().getNano() % 1000);

        AttemptView saved = attempts.saveForLater(seed.userId(), started.id());
        AttemptView savedAgain = attempts.get(seed.userId(), started.id());
        assertNotNull(saved.savedAt());
        assertEquals(saved.savedAt(), savedAgain.savedAt());
        assertEquals(started.deadlineAt(), savedAgain.deadlineAt());

        AttemptView resumed = attempts.resume(seed.userId(), started.id());
        assertNull(resumed.savedAt());
        assertEquals(started.deadlineAt(), resumed.deadlineAt());

        ResultView finished = attempts.finish(seed.userId(), started.id());
        ResultView repeated = attempts.finish(seed.userId(), started.id());
        assertNotNull(finished.completedAt());
        assertEquals(finished.completedAt(), repeated.completedAt());
        assertEquals(finished.startedAt(), repeated.startedAt());
    }

    @ParameterizedTest
    @EnumSource(ExpiredAction.class)
    void rejectedSaveOrResumeStillCommitsExpiredResult(ExpiredAction action) {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        attempts.answer(seed.userId(), attempt.id(), attempt.questions().get(0).id(),
                new AnswerRequest(Set.of(), new BigDecimal("12.5")));
        Instant deadline = expire(attempt.id(), 120);

        ApiException error = assertThrows(ApiException.class, () -> {
            if (action == ExpiredAction.SAVE) attempts.saveForLater(seed.userId(), attempt.id());
            else attempts.resume(seed.userId(), attempt.id());
        });
        assertEquals("ATTEMPT_NOT_ACTIVE", error.getCode());
        assertEquals(409, error.getStatus().value());
        TestAttempt persisted = repository.findById(attempt.id()).orElseThrow();
        assertEquals(AttemptStatus.EXPIRED, persisted.getStatus());
        assertEquals(deadline, persisted.getCompletedAt(), "Completion must use the deadline, not scheduler/request delay");
        assertEquals(1, persisted.getCorrectAnswers());
        assertEquals(3600L, attempts.myResult(seed.userId(), attempt.id()).elapsedSeconds());
    }

    @Test
    void requestingAnOverdueResultGradesItWithoutWaitingForScheduler() {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        Instant deadline = expire(attempt.id(), 90);
        ResultView result = attempts.myResult(seed.userId(), attempt.id());
        assertEquals(AttemptStatus.EXPIRED, result.status());
        assertEquals(deadline, result.completedAt());
        assertEquals(3600, result.elapsedSeconds());
    }

    @Test
    void startExpiresOverdueAttemptAndUsesActualDeadlineForRetakeDelay() {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView old = attempts.start(seed.userId(), seed.categoryId());
        expire(old.id(), 48 * 3600);
        AttemptView next = attempts.start(seed.userId(), seed.categoryId());
        assertNotEquals(old.id(), next.id());
        assertEquals(AttemptStatus.IN_PROGRESS, next.status());
        assertEquals(AttemptStatus.EXPIRED, repository.findById(old.id()).orElseThrow().getStatus());
    }

    @Test
    void deniedRetakeStillPersistsPreviousExpiry() {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView old = attempts.start(seed.userId(), seed.categoryId());
        expire(old.id(), 10);
        ApiException error = assertThrows(ApiException.class, () -> attempts.start(seed.userId(), seed.categoryId()));
        assertEquals("RETAKE_NOT_ALLOWED", error.getCode());
        assertEquals(AttemptStatus.EXPIRED, repository.findById(old.id()).orElseThrow().getStatus());
    }

    @Test
    void clearingAnAnswerDoesNotCountAsAnsweredInSavedProgress() {
        Seed seed = seed(AnswerType.SINGLE);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        TestQuestionView question = attempt.questions().get(0);
        attempts.answer(seed.userId(), attempt.id(), question.id(), new AnswerRequest(Set.of(question.options().get(0).id()), null));
        assertEquals(1, attempts.savedAttempts(seed.userId()).get(0).answeredQuestions());
        attempts.answer(seed.userId(), attempt.id(), question.id(), new AnswerRequest(Set.of(), null));
        assertEquals(0, attempts.savedAttempts(seed.userId()).get(0).answeredQuestions());
    }

    @Test
    void savedOptionIdsRemainReadableAfterServiceTransactionCloses() {
        Seed seed = seed(AnswerType.SINGLE);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        TestQuestionView question = attempt.questions().get(0);
        Long selectedId = question.options().get(0).id();
        attempts.answer(seed.userId(), attempt.id(), question.id(), new AnswerRequest(Set.of(selectedId), null));
        // This separate request loads an existing answer from the database.
        // Iterating the returned set emulates JSON serialization after commit.
        AttemptView reloaded = attempts.get(seed.userId(), attempt.id());
        Set<Long> selected = reloaded.questions().get(0).selectedOptionIds();
        assertEquals(1, selected.size());
        assertTrue(selected.contains(selectedId));
    }

    @Test
    void numericValuesOutsideDatabasePrecisionAreRejectedBeforePersistence() {
        Seed seed = seed(AnswerType.NUMERIC);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        for (String invalid : List.of("1E+20", "123456789012345", "0.0000001")) {
            ApiException error = assertThrows(ApiException.class, () -> attempts.answer(seed.userId(), attempt.id(),
                    attempt.questions().get(0).id(), new AnswerRequest(Set.of(), new BigDecimal(invalid))));
            assertEquals("INVALID_NUMERIC_ANSWER", error.getCode());
            assertEquals(400, error.getStatus().value());
        }
        AttemptView accepted = attempts.answer(seed.userId(), attempt.id(), attempt.questions().get(0).id(),
                new AnswerRequest(Set.of(), new BigDecimal("99999999999999.999999")));
        assertEquals(new BigDecimal("99999999999999.999999"), accepted.questions().get(0).numericValue());
    }

    @Test
    void replacingQuestionOptionsReturnsPersistedIdsImmediately() {
        Seed seed = seed(AnswerType.SINGLE);
        Long questionId = tx().execute(status -> questions.findByCategoryIdAndActiveTrue(seed.categoryId()).get(0).getId());
        QuestionRequest edit = new QuestionRequest(seed.categoryId(), "Updated question", null, "EASY", AnswerType.SINGLE,
                null, null, List.of(new OptionInput("New correct", null, true), new OptionInput("New wrong", null, false)));
        QuestionView updated = admin.updateQuestion(questionId, edit, seed.userId());
        assertEquals(2, updated.options().size());
        assertTrue(updated.options().stream().allMatch(option -> option.id() != null));
        QuestionView reloaded = admin.question(questionId);
        assertEquals(updated.options().stream().map(OptionView::id).sorted().toList(),
                reloaded.options().stream().map(OptionView::id).sorted().toList());
    }

    @Test
    void twoConcurrentStartsCreateExactlyOneActiveAttempt() throws Exception {
        Seed seed = seed(AnswerType.NUMERIC);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> request = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    attempts.start(seed.userId(), seed.categoryId());
                    return "STARTED";
                } catch (ApiException exception) {
                    return exception.getCode();
                }
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("STARTED"::equals).count());
            assertEquals(1, outcomes.stream().filter("ATTEMPT_IN_PROGRESS"::equals).count());
            assertEquals(1, attempts.savedAttempts(seed.userId()).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void readingFortyQuestionResultDoesNotQueryAnswersIndividually() {
        Seed seed = seed(AnswerType.SINGLE);
        AttemptView attempt = attempts.start(seed.userId(), seed.categoryId());
        for (TestQuestionView question : attempt.questions()) {
            attempts.answer(seed.userId(), attempt.id(), question.id(), new AnswerRequest(Set.of(question.options().get(0).id()), null));
        }
        attempts.finish(seed.userId(), attempt.id());
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabledBefore = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            ResultView result = attempts.myResult(seed.userId(), attempt.id());
            assertEquals(40, result.totalQuestions());
            assertTrue(statistics.getPrepareStatementCount() < 15,
                    "A single result must use batched answers instead of 40 individual queries; actual=" + statistics.getPrepareStatementCount());
        } finally {
            statistics.setStatisticsEnabled(enabledBefore);
        }
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }

    private Instant expire(Long attemptId, long secondsAgo) {
        Instant deadline = Instant.now().minusSeconds(secondsAgo).truncatedTo(ChronoUnit.SECONDS);
        tx().executeWithoutResult(status -> {
            TestAttempt attempt = repository.findById(attemptId).orElseThrow();
            attempt.setStartedAt(deadline.minusSeconds(3600));
            attempt.setDeadlineAt(deadline);
        });
        return deadline;
    }

    private Seed seed(AnswerType type) {
        return tx().execute(status -> {
            Category category = new Category();
            category.setName("Regression " + UUID.randomUUID());
            category.setDurationMinutes(60);
            category.setQuestionCount(40);
            category.setAdditionalQuestionCount(0);
            category.setRetakeDelayHours(24);
            categories.save(category);
            UserAccount user = new UserAccount("+9989" + NEXT_PHONE.incrementAndGet(), "test-unused-" + UUID.randomUUID(), "Test", "Person", true);
            user.setEmail(UUID.randomUUID() + "@gmail.com");
            user.setBirthDate(LocalDate.of(1990, 1, 1));
            user.setRegion("Region"); user.setDistrict("District"); user.setAddress("Address");
            user.setProfessionCategory(category);
            users.save(user);
            for (int i = 0; i < 40; i++) {
                Question question = new Question();
                question.setCategory(category); question.setBody("Regression question " + i);
                question.setAnswerType(type);
                if (type == AnswerType.NUMERIC) {
                    question.setNumericAnswer(new BigDecimal("12.5"));
                    question.setNumericTolerance(new BigDecimal("0.1"));
                } else {
                    AnswerOption correct = new AnswerOption(); correct.setLabel("Correct"); correct.setCorrect(true);
                    AnswerOption wrong = new AnswerOption(); wrong.setLabel("Wrong"); wrong.setCorrect(false);
                    question.replaceOptions(List.of(correct, wrong));
                }
                questions.save(question);
            }
            questions.flush();
            return new Seed(user.getId(), category.getId());
        });
    }

    private record Seed(Long userId, Long categoryId) {}
}
