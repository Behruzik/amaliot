package com.example.amaliyot.service.answer;

import com.example.amaliyot.domain.AnswerType;
import org.springframework.stereotype.Component;

@Component
public class MultipleAnswerEvaluator extends AbstractChoiceAnswerEvaluator {
    public MultipleAnswerEvaluator() {
        super(AnswerType.MULTIPLE);
    }
}
