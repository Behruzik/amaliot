package com.example.amaliyot;

import com.example.amaliyot.config.DemoDataLoader;
import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.Category;
import com.example.amaliyot.domain.Entities.Question;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.*;
import com.example.amaliyot.service.AuthService;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.service.CertificateService;
import com.example.amaliyot.service.SuperAdminDataLoader;
import com.example.amaliyot.web.ApiDtos.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Starts the real demo loader in its own disposable database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:amaliyot-demo-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.config.import=",
        "app.scheduling.enabled=false"
})
@ActiveProfiles({"test", "demo"})
class DemoProfileTests {
    @Autowired ApplicationContext context;
    @Autowired DemoDataLoader loader;
    @Autowired UserAccountRepository users;
    @Autowired CategoryRepository categories;
    @Autowired QuestionRepository questions;
    @Autowired TestAttemptRepository attemptRepository;
    @Autowired CertificateRepository certificateRepository;
    @Autowired AuthService auth;
    @Autowired AttemptService attempts;
    @Autowired CertificateService certificates;

    @Test
    void demoCreatesTwoWorkingAccountsWithoutProductionBootstrap() {
        assertEquals(2, users.count());
        assertTrue(context.getBeansOfType(SuperAdminDataLoader.class).isEmpty(),
                "The production account bootstrap must not run in demo mode");
        var admin = auth.login(new LoginRequest("admin.demo@example.test", "DemoAdmin123!"));
        var student = auth.login(new LoginRequest("student.demo@gmail.com", "DemoUser123!"));
        assertEquals(Role.SUPER_ADMIN, admin.user().role());
        assertEquals(Role.USER, student.user().role());
        assertNotNull(admin.accessToken());
        assertNotNull(student.accessToken());
        assertNotEquals(admin.user().id(), student.user().id());
    }

    @Test
    void demoContainsExactlyFortyQuestionsCoveringEverySupportedAnswerType() {
        List<Question> bank = questions.findByCategoryIdAndActiveTrue(demoCategory().getId());
        assertEquals(40, bank.size());
        Map<AnswerType, Long> counts = bank.stream().collect(Collectors.groupingBy(Question::getAnswerType, Collectors.counting()));
        assertEquals(30L, counts.get(AnswerType.SINGLE));
        assertEquals(5L, counts.get(AnswerType.MULTIPLE));
        assertEquals(5L, counts.get(AnswerType.NUMERIC));
        for (Question question : bank) {
            if (question.getAnswerType() == AnswerType.NUMERIC) {
                assertNotNull(question.getNumericAnswer());
                assertTrue(question.getNumericTolerance().compareTo(BigDecimal.ZERO) >= 0);
            } else {
                assertTrue(question.getOptions().size() >= 2);
                assertTrue(question.getOptions().stream().anyMatch(option -> option.isCorrect()));
            }
        }
    }

    @Test
    void demoStudentStartsWith38Of40ResultAndValidCertificate() {
        UserAccount student = student();
        var seeded = attemptRepository.findByUserId(student.getId());
        assertEquals(1, seeded.size());
        var result = attempts.myResult(student.getId(), seeded.get(0).getId());
        assertEquals(AttemptStatus.PENDING, result.status());
        assertEquals(38, result.correctAnswers());
        assertEquals(40, result.totalQuestions());
        assertEquals(0, new BigDecimal("95.00").compareTo(result.score()));
        var issued = certificates.myCertificates(student.getId());
        assertEquals(1, issued.size());
        assertTrue(issued.get(0).valid());
        assertEquals(38, issued.get(0).correctAnswers());
        assertEquals(40, issued.get(0).totalQuestions());
        assertEquals(issued.get(0).certificateNumber(), certificates.verify(issued.get(0).certificateNumber()).certificateNumber());
    }

    @Test
    void runningDemoLoaderAgainPreservesIdsPasswordsResultsAndCertificates() throws Exception {
        UserAccount before = student();
        long userCount = users.count();
        long questionCount = questions.count();
        long attemptCount = attemptRepository.count();
        long certificateCount = certificateRepository.count();
        Long categoryId = demoCategory().getId();
        String verifier = before.getPasswordHash();
        String certificateNumber = certificates.myCertificates(before.getId()).get(0).certificateNumber();

        loader.run(new DefaultApplicationArguments());
        loader.run(new DefaultApplicationArguments());

        assertEquals(userCount, users.count());
        assertEquals(questionCount, questions.count());
        assertEquals(attemptCount, attemptRepository.count());
        assertEquals(certificateCount, certificateRepository.count());
        assertEquals(categoryId, demoCategory().getId());
        assertEquals(before.getId(), student().getId());
        assertEquals(verifier, student().getPasswordHash(), "Re-running startup must not reset account credentials");
        assertEquals(certificateNumber, certificates.myCertificates(before.getId()).get(0).certificateNumber());
    }

    private UserAccount student() {
        return users.findByEmailIgnoreCase("student.demo@gmail.com").orElseThrow();
    }

    private Category demoCategory() {
        return categories.findAll().stream().filter(category -> DemoDataLoader.CATEGORY_NAME.equals(category.getName()))
                .findFirst().orElseThrow();
    }
}
