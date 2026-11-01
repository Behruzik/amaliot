package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Entities.AttemptQuestion;
import com.example.amaliyot.web.ApiDtos.AnswerRequest;

import java.util.Objects;

/** Shared template-method behavior for answer evaluators. */
public abstract class AbstractAnswerEvaluator implements AnswerEvaluator {
    private final AnswerType answerType;

    protected AbstractAnswerEvaluator(AnswerType answerType) {
        this.answerType = Objects.requireNonNull(answerType);
    }

    @Override
    public final AnswerType answerType() {
        return answerType;
    }

    @Override
    public final void validate(AttemptQuestion question, AnswerRequest request) {
        if (question.getAnswerType() != answerType) {
            throw new IllegalArgumentException("Evaluator was selected for the wrong answer type");
        }
        validateAnswer(question, request);
    }

    protected abstract void validateAnswer(AttemptQuestion question, AnswerRequest request);
}
