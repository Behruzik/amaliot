package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.QuestionRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.web.ApiDtos.RecommendationView;
import com.example.amaliyot.web.ApiDtos.RecommendedTestView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecommendationService {
    private final UserAccountRepository users;
    private final QuestionRepository questions;
    public RecommendationService(UserAccountRepository users, QuestionRepository questions) {
        this.users = users;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public RecommendationView forUser(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        if (user.getProfessionCategory() == null)
            throw ApiException.conflict("PROFILE_PROFESSION_REQUIRED", "Choose your profession in the profile to receive recommendations");

        var profession = user.getProfessionCategory();
        int activeQuestions = questions.findByGlobalPoolTrueAndActiveTrueAndDifficulty(
                com.example.amaliyot.domain.Difficulty.EASY).size();
        if (activeQuestions == 0) activeQuestions = questions.findByCategoryIdAndActiveTrue(profession.getId()).size();
        RecommendedTestView test = new RecommendedTestView(profession.getId(), profession.getName(),
                profession.getDescription(), activeQuestions, profession.getQuestionCount(),
                profession.isActive() && activeQuestions >= profession.getQuestionCount());

        return new RecommendationView(profession.getId(), profession.getName(), List.of(test));
    }
}
