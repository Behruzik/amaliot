package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.Entities.AttemptAnswer;
import com.example.amaliyot.domain.Entities.AttemptQuestion;
import com.example.amaliyot.web.ApiDtos.AnswerRequest;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class NumericAnswerEvaluator extends AbstractAnswerEvaluator {
    public NumericAnswerEvaluator() {
        super(AnswerType.NUMERIC);
    }

    @Override
    protected void validateAnswer(AttemptQuestion question, AnswerRequest request) {
        Set<Long> selected = request.selectedOptionIds() == null ? Set.of() : request.selectedOptionIds();
        if (!selected.isEmpty()) {
            throw ApiException.badRequest("INVALID_ANSWER", "Numeric questions do not accept option ids");
        }
        if (request.numericValue() != null && !fitsNumericColumn(request.numericValue())) {
            throw ApiException.badRequest("INVALID_NUMERIC_ANSWER",
                    "Numeric answer supports at most 14 integer digits and 6 decimal places");
        }
    }

    @Override
    public boolean isCorrect(AttemptQuestion question, AttemptAnswer answer) {
        if (answer == null || answer.getNumericValue() == null || question.getNumericAnswer() == null) return false;
        BigDecimal tolerance = question.getNumericTolerance() == null
                ? BigDecimal.ZERO : question.getNumericTolerance();
        return answer.getNumericValue().subtract(question.getNumericAnswer()).abs().compareTo(tolerance) <= 0;
    }

    private boolean fitsNumericColumn(BigDecimal value) {
        return value.precision() - value.scale() <= 14 && value.scale() <= 6;
    }
}
