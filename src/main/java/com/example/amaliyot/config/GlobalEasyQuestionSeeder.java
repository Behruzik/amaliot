package com.example.amaliyot.config;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Difficulty;
import com.example.amaliyot.domain.Entities.AnswerOption;
import com.example.amaliyot.domain.Entities.Question;
import com.example.amaliyot.repository.QuestionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Installs a shared set of 400 beginner surveying questions on a fresh database. */
@Component
@Order(0)
@Profile("!test")
public class GlobalEasyQuestionSeeder implements ApplicationRunner {
    private final QuestionRepository questions;

    public GlobalEasyQuestionSeeder(QuestionRepository questions) {
        this.questions = questions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Question> existing = questions.findByGlobalPoolTrueAndActiveTrueAndDifficulty(Difficulty.EASY);
        if (existing.size() >= 400) return;

        List<Seed> seeds = new ArrayList<>(400);
        for (int value = 1; value <= 80; value++) {
            seeds.add(conversion(value + " km necha metrga teng?", value * 1_000d, "m"));
            seeds.add(conversion(value + " metr necha santimetrga teng?", value * 100d, "cm"));
        }
        int[] scales = {100, 500, 1_000, 2_500};
        for (int scale : scales) {
            for (int planCm = 1; planCm <= 20; planCm++) {
                double groundMeters = (double) scale * planCm / 100;
                seeds.add(conversion("1:" + scale + " masshtabli xaritada " + planCm
                        + " cm masofa joyda necha metr bo‘ladi?", groundMeters, "m"));
            }
        }
        for (int hectares = 1; hectares <= 80; hectares++) {
            seeds.add(conversion(hectares + " gektar necha kvadrat metrga teng?", hectares * 10_000d, "m²"));
        }
        for (int hectares = 1; hectares <= 40; hectares++) {
            seeds.add(conversion((hectares * 10_000) + " m² necha gektarga teng?", hectares, "ga"));
        }
        for (int degrees = 1; degrees <= 20; degrees++) {
            seeds.add(conversion(degrees + " gradus necha burchak minutiga teng?", degrees * 60d, "′"));
            seeds.add(conversion(degrees + " burchak minuti necha burchak sekundiga teng?", degrees * 60d, "″"));
        }
        if (seeds.size() != 400) throw new IllegalStateException("Expected 400 global easy questions");

        Set<String> existingPrompts = new HashSet<>();
        for (Question question : existing) existingPrompts.add(question.getBody());
        int remaining = 400 - existing.size();

        for (int index = 0; index < seeds.size() && remaining > 0; index++) {
            Seed seed = seeds.get(index);
            if (!existingPrompts.add(seed.prompt())) continue;
            Question question = new Question();
            question.setBody(seed.prompt());
            question.setDifficulty(Difficulty.EASY);
            question.setAnswerType(AnswerType.SINGLE);
            question.setGlobalPool(true);

            List<Double> values = new ArrayList<>(List.of(seed.answer(), seed.answer() + 1,
                    seed.answer() * 10, seed.answer() / 10));
            for (int i = 0; i < values.size(); i++) {
                for (int j = i + 1; j < values.size(); j++) {
                    if (Math.abs(values.get(i) - values.get(j)) < 0.000001) values.set(j, seed.answer() + j + 2);
                }
            }
            Collections.shuffle(values, new Random(index * 7919L + 17));
            List<AnswerOption> options = new ArrayList<>(4);
            for (double value : values) {
                AnswerOption option = new AnswerOption();
                option.setLabel(format(value) + " " + seed.unit());
                option.setCorrect(Math.abs(value - seed.answer()) < 0.000001);
                options.add(option);
            }
            question.replaceOptions(options);
            questions.save(question);
            remaining--;
        }
        if (remaining > 0) throw new IllegalStateException("Could not fill the shared bank to 400 questions");
        questions.flush();
    }

    private static Seed conversion(String prompt, double answer, String unit) {
        return new Seed(prompt, answer, unit);
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private record Seed(String prompt, double answer, String unit) {}
}
