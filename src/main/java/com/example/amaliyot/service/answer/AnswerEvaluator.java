package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Entities.AttemptAnswer;
import com.example.amaliyot.domain.Entities.AttemptQuestion;
import com.example.amaliyot.web.ApiDtos.AnswerRequest;

/** Contract for validating and grading one answer format. */
public interface AnswerEvaluator {
    AnswerType answerType();

    void validate(AttemptQuestion question, AnswerRequest request);

    boolean isCorrect(AttemptQuestion question, AttemptAnswer answer);
}
