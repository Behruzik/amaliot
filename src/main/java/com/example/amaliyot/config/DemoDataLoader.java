package com.example.amaliyot.config;

import com.example.amaliyot.domain.Entities.*;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.*;
import com.example.amaliyot.service.AttemptService;
import com.example.amaliyot.service.PasswordStorageService;
import com.example.amaliyot.web.ApiDtos.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Synthetic data, enabled only in a disposable in-memory demo database. */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    public static final String CATEGORY_NAME = "Демо: основы измерений / O‘lchash asoslari";
    private final DataSource dataSource;
    private final Environment environment;
    private final UserAccountRepository users;
    private final CategoryRepository categories;
    private final GeographicRegionRepository regions;
    private final GeographicDistrictRepository districts;
    private final TestAttemptRepository attempts;
    private final PasswordStorageService passwords;
    private final AttemptService exams;

    public DemoDataLoader(DataSource dataSource, Environment environment, UserAccountRepository users,
                          CategoryRepository categories,
                          GeographicRegionRepository regions, GeographicDistrictRepository districts,
                          TestAttemptRepository attempts, PasswordStorageService passwords, AttemptService exams) {
        this.dataSource = dataSource; this.environment = environment; this.users = users;
        this.categories = categories; this.regions = regions;
        this.districts = districts; this.attempts = attempts; this.passwords = passwords; this.exams = exams;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("The demo and prod profiles must not be combined");
        }
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:amaliyot-demo")) {
                throw new IllegalStateException("Demo data is only permitted in the disposable amaliyot-demo H2 database");
            }
        }
        Category category = categories.findAll().stream()
                .filter(value -> CATEGORY_NAME.equals(value.getName())).findFirst().orElseGet(() -> {
                    Category created = new Category();
                    created.setName(CATEGORY_NAME);
                    created.setDescription("Синтетические вопросы только для проверки API; это не учебный экзамен.");
                    created.setDurationMinutes(60); created.setQuestionCount(16);
                    created.setAdditionalQuestionCount(0); created.setRetakeDelayHours(0);
                    return categories.saveAndFlush(created);
                });
        GeographicRegion region = regions.findByNameIgnoreCaseAndActiveTrue("Demo region").orElseGet(() -> {
            GeographicRegion created = new GeographicRegion(); created.setName("Demo region");
            return regions.saveAndFlush(created);
        });
        if (!districts.existsByRegion_IdAndNameIgnoreCase(region.getId(), "Demo district")) {
            GeographicDistrict district = new GeographicDistrict();
            district.setRegion(region); district.setName("Demo district"); districts.save(district);
        }
        UserAccount admin = account("admin.demo@example.test", "+998900000001", "DemoAdmin123!", Role.SUPER_ADMIN, category);
        UserAccount student = account("student.demo@gmail.com", "+998900000002", "DemoUser123!", Role.USER, category);
        if (attempts.findByUserId(student.getId()).isEmpty()) {
            var view = exams.start(student.getId(), category.getId());
            TestAttempt snapshot = attempts.findWithSnapshotById(view.id()).orElseThrow();
            for (AttemptQuestion question : snapshot.getQuestions()) {
                Set<Long> selected = question.getOptions().stream().filter(AttemptOption::isCorrect)
                        .map(AttemptOption::getId).collect(Collectors.toSet());
                exams.answer(student.getId(), view.id(), question.getId(),
                        new AnswerRequest(selected, null));
            }
            exams.finish(student.getId(), view.id());
        }
        log.warn("DEMO profile is active: disposable database, a shared bank of 400 generated beginner questions, and public demo credentials. See README.md.");
    }

    private UserAccount account(String email, String phone, String password, Role role, Category category) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserAccount user = new UserAccount(phone, "", "Демо", role == Role.USER ? "Ученик" : "Администратор", true);
            user.setEmail(email); user.setRole(role); user.setProfessionCategory(category);
            user.setBirthDate(LocalDate.of(2000, 1, 1)); user.setRegion("Demo region");
            user.setDistrict("Demo district"); user.setAddress("Демонстрационный адрес");
            passwords.setPassword(user, password);
            return users.saveAndFlush(user);
        });
    }

}
