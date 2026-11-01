package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SingleAnswerEvaluator extends AbstractChoiceAnswerEvaluator {
    public SingleAnswerEvaluator() {
        super(AnswerType.SINGLE);
    }

    @Override
    protected void validateSelection(Set<Long> selected) {
        if (selected.size() > 1) {
            throw ApiException.badRequest("INVALID_ANSWER", "Choose at most one answer");
        }
    }
}
