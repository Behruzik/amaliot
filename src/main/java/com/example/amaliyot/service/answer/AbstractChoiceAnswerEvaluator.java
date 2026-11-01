package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Entities.AttemptAnswer;
import com.example.amaliyot.domain.Entities.AttemptOption;
import com.example.amaliyot.domain.Entities.AttemptQuestion;
import com.example.amaliyot.web.ApiDtos.AnswerRequest;
import com.example.amaliyot.web.ApiException;

import java.util.Set;
import java.util.stream.Collectors;

/** Common validation and exact-set grading for choice questions. */
public abstract class AbstractChoiceAnswerEvaluator extends AbstractAnswerEvaluator {
    protected AbstractChoiceAnswerEvaluator(AnswerType answerType) {
        super(answerType);
    }

    @Override
    protected final void validateAnswer(AttemptQuestion question, AnswerRequest request) {
        Set<Long> selected = request.selectedOptionIds() == null ? Set.of() : request.selectedOptionIds();
        if (request.numericValue() != null) {
            throw ApiException.badRequest("INVALID_ANSWER", "Choice questions do not accept a numeric answer");
        }
        Set<Long> available = question.getOptions().stream().map(AttemptOption::getId).collect(Collectors.toSet());
        if (!available.containsAll(selected)) {
            throw ApiException.badRequest("INVALID_OPTION", "An answer option does not belong to this question");
        }
        validateSelection(selected);
    }

    protected void validateSelection(Set<Long> selected) { }

    @Override
    public final boolean isCorrect(AttemptQuestion question, AttemptAnswer answer) {
        if (answer == null) return false;
        Set<Long> expected = question.getOptions().stream().filter(AttemptOption::isCorrect)
                .map(AttemptOption::getId).collect(Collectors.toSet());
        Set<Long> selected = answer.getSelectedOptionIds() == null ? Set.of() : answer.getSelectedOptionIds();
        return !expected.isEmpty() && expected.equals(selected);
    }
}
