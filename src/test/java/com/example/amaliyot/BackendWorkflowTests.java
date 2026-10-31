package com.example.amaliyot;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.*;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.*;
import com.example.amaliyot.security.AuthPrincipal;
import com.example.amaliyot.security.JwtService;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.service.AuthService;
import com.example.amaliyot.service.PasswordStorageService;
import com.example.amaliyot.web.AdminController;
import com.example.amaliyot.web.ApiDtos.*;
import com.example.amaliyot.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BackendWorkflowTests {
    private static final AtomicLong NEXT_PHONE = new AtomicLong(90_000_000);
    @Autowired UserAccountRepository users;
    @Autowired CategoryRepository categories;
    @Autowired QuestionRepository questions;
    @Autowired TestAttemptRepository attemptsRepository;
    @Autowired RetakeGrantRepository retakeGrants;
    @Autowired RevokedTokenRepository revokedTokens;
    @Autowired AttemptService attempts;
    @Autowired AuthService auth;
    @Autowired PasswordStorageService passwords;
    @Autowired JwtService jwt;
    @Autowired AdminController adminController;

    @Test
    @Transactional
    void signupHashesPasswordLoginAndLogoutRevokesToken() {
        assertThrows(ApiException.class, () -> auth.register(registration("12345")));
        RegisterRequest request = registration("StrongPass123");
        AuthResponse registered = auth.register(request);
        UserAccount account = users.findById(registered.user().id()).orElseThrow();
        assertNotEquals("StrongPass123", account.getPasswordHash());
        assertTrue(passwords.matchesAndUpgrade("StrongPass123", account));
        assertEquals(Role.USER, account.getRole());

        AuthResponse login = auth.login(new LoginRequest(account.getEmail(), "StrongPass123"));
        assertNotNull(auth.login(new LoginRequest(account.getPhone(), "StrongPass123")).accessToken());
        String tokenId = jwt.parse(login.accessToken()).tokenId();
        auth.logout("Bearer " + login.accessToken());
        assertTrue(revokedTokens.existsById(tokenId));
        ApiException duplicate = assertThrows(ApiException.class, () -> auth.register(request));
        assertEquals("EMAIL_ALREADY_REGISTERED", duplicate.getCode());
    }

    @Test
    @Transactional
    void passwordChangeRevokesExistingTokensAndAcceptsNewPassword() {
        AuthResponse registered = auth.register(registration("StrongPass123"));
        UserAccount user = users.findById(registered.user().id()).orElseThrow();
        AuthResponse olderSession = auth.login(new LoginRequest(user.getEmail(), "StrongPass123"));
        auth.changePassword(user.getId(), new ChangePasswordRequest("StrongPass123", "NewStrongPass123"),
                "Bearer " + olderSession.accessToken());
        assertEquals(1L, user.getTokenVersion());
        assertTrue(revokedTokens.existsById(jwt.parse(olderSession.accessToken()).tokenId()));
        assertNotNull(auth.login(new LoginRequest(user.getEmail(), "NewStrongPass123")).accessToken());
    }

    @Test
    @Transactional
    void repeatedBadLoginsAreThrottled() {
        AuthResponse registered = auth.register(registration("StrongPass123"));
        String email = registered.user().email();
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> auth.login(new LoginRequest(email, "wrong-password")));
        }
        ApiException limited = assertThrows(ApiException.class, () -> auth.login(new LoginRequest(email, "wrong-password")));
        assertEquals(429, limited.getStatus().value());
    }

    @Test
    @Transactional
    void userCannotReadAnotherUsersAttemptAndUserEndpointDoesNotExposeKeys() {
        Category category = examCategory();
        UserAccount owner = newUser(category);
        AttemptView started = attempts.start(owner.getId(), category.getId());
        assertEquals(40, started.questions().size());
        assertEquals(2, started.questions().get(0).options().size());
        assertTrue(java.util.Arrays.stream(TestOptionView.class.getRecordComponents()).noneMatch(c -> c.getName().equals("correct")));
        Long questionId = started.questions().get(0).id();
        Long correctOptionId = correctSnapshotOptionId(started.id(), questionId);
        Long wrongOptionId = started.questions().get(0).options().stream().map(TestOptionView::id)
                .filter(id -> !id.equals(correctOptionId)).findFirst().orElseThrow();
        attempts.answer(owner.getId(), started.id(), questionId, new AnswerRequest(Set.of(wrongOptionId), null));
        attempts.answer(owner.getId(), started.id(), questionId, new AnswerRequest(Set.of(correctOptionId), null));

        UserAccount another = newUser(category);
        ApiException hidden = assertThrows(ApiException.class, () -> attempts.get(another.getId(), started.id()));
        assertEquals(404, hidden.getStatus().value());

        ResultView result = attempts.finish(owner.getId(), started.id());
        assertEquals(1, result.correctAnswers());
        assertEquals(2.5, result.score().doubleValue());
        assertEquals(AttemptStatus.PENDING, result.status());
        ResultView repeated = attempts.finish(owner.getId(), started.id());
        assertEquals(result.correctAnswers(), repeated.correctAnswers());
        assertEquals(result.completedAt(), repeated.completedAt());
    }

    @Test
    @Transactional
    void answerScoringAndServerDeadlineAreEnforced() {
        Category category = examCategory();
        UserAccount owner = newUser(category);
        AttemptView started = attempts.start(owner.getId(), category.getId());
        Long questionId = started.questions().get(0).id();
        Long correctOptionId = correctSnapshotOptionId(started.id(), questionId);
        Long wrongOptionId = started.questions().get(0).options().stream().map(TestOptionView::id)
                .filter(id -> !id.equals(correctOptionId)).findFirst().orElseThrow();
        attempts.answer(owner.getId(), started.id(), questionId, new AnswerRequest(Set.of(wrongOptionId), null));
        ResultView result = attempts.finish(owner.getId(), started.id());
        assertEquals(0, result.correctAnswers());
        assertEquals(0.0, result.score().doubleValue());

        Category timedCategory = examCategory();
        owner.setProfessionCategory(timedCategory);
        users.saveAndFlush(owner);
        AttemptView timed = attempts.start(owner.getId(), timedCategory.getId());
        TestAttempt persisted = attemptsRepository.findWithSnapshotById(timed.id()).orElseThrow();
        persisted.setDeadlineAt(Instant.now().minusSeconds(1));
        attemptsRepository.saveAndFlush(persisted);
        attempts.expireDueAttempts();
        AttemptView expired = attempts.get(owner.getId(), timed.id());
        assertEquals(AttemptStatus.EXPIRED, expired.status());
        assertNotNull(expired.completedAt());
        assertThrows(ApiException.class, () -> attempts.answer(owner.getId(), timed.id(), timed.questions().get(0).id(), new AnswerRequest(Set.of(), null)));
    }

    @Test
    @Transactional
    void retakeDelayCanBeOverriddenOnceByGrant() {
        Category category = examCategory();
        UserAccount owner = newUser(category);
        AttemptView first = attempts.start(owner.getId(), category.getId());
        attempts.finish(owner.getId(), first.id());
        assertThrows(ApiException.class, () -> attempts.start(owner.getId(), category.getId()));

        RetakeGrant grant = new RetakeGrant(); grant.setUser(owner); grant.setCategory(category); grant.setGrantedById(owner.getId());
        retakeGrants.saveAndFlush(grant);
        AttemptView second = attempts.start(owner.getId(), category.getId());
        assertTrue(grant.isConsumed());
        attempts.finish(owner.getId(), second.id());
        ApiException denied = assertThrows(ApiException.class, () -> attempts.start(owner.getId(), category.getId()));
        assertEquals("RETAKE_NOT_ALLOWED", denied.getCode());
    }

    @Test
    @Transactional
    void regularUserIsForbiddenFromAdminApi() {
        UserAccount user = newUser(examCategory());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(user.getId(), Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThrows(AccessDeniedException.class, () -> adminController.dashboard());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private RegisterRequest registration(String password) {
        return new RegisterRequest(UUID.randomUUID() + "@gmail.com", phone(), password,
                "Test", "Person", null, true, null, password);
    }

    private UserAccount newUser(Category category) {
        UserAccount user = new UserAccount(phone(), "test-unused-" + UUID.randomUUID(), "Test", "Person", true);
        user.setEmail(UUID.randomUUID() + "@gmail.com");
        user.setBirthDate(LocalDate.of(1990, 1, 1)); user.setRegion("Region"); user.setDistrict("District"); user.setAddress("Address");
        user.setProfessionCategory(category);
        return users.saveAndFlush(user);
    }

    private Category examCategory() {
        Category category = new Category(); category.setName("Test category " + UUID.randomUUID()); category.setActive(true);
        category.setDurationMinutes(60); category.setQuestionCount(40); category.setAdditionalQuestionCount(0); category.setRetakeDelayHours(24);
        categories.saveAndFlush(category);
        for (int i = 0; i < 40; i++) {
            Question question = new Question(); question.setCategory(category); question.setBody("Choose the correct answer " + i);
            question.setDifficulty("EASY"); question.setAnswerType(AnswerType.SINGLE);
            AnswerOption correct = new AnswerOption(); correct.setLabel("Correct"); correct.setCorrect(true); correct.setQuestion(question);
            AnswerOption wrong = new AnswerOption(); wrong.setLabel("Wrong"); wrong.setCorrect(false); wrong.setQuestion(question);
            question.getOptions().add(correct); question.getOptions().add(wrong);
            questions.save(question);
        }
        questions.flush();
        return category;
    }

    private Long correctSnapshotOptionId(Long attemptId, Long questionId) {
        TestAttempt attempt = attemptsRepository.findWithSnapshotById(attemptId).orElseThrow();
        return attempt.getQuestions().stream().filter(q -> q.getId().equals(questionId)).findFirst().orElseThrow()
                .getOptions().stream().filter(AttemptOption::isCorrect).findFirst().orElseThrow().getId();
    }

    private String phone() { return "+9989" + NEXT_PHONE.incrementAndGet(); }
}
