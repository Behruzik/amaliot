package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.Certificate;
import com.example.amaliyot.domain.Entities.TestAttempt;
import com.example.amaliyot.repository.CertificateRepository;
import com.example.amaliyot.web.ApiDtos.CertificateView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CertificateService {
    public static final int EXAM_QUESTION_COUNT = 16;
    public static final int PASSING_CORRECT_ANSWERS = 14;

    private final CertificateRepository certificates;

    public CertificateService(CertificateRepository certificates) {
        this.certificates = certificates;
    }

    @Transactional
    public void issueForPassedAttempt(TestAttempt attempt) {
        if (attempt.getTotalQuestions() != attempt.getCategory().getQuestionCount()
                || attempt.getCorrectAnswers() < passingCorrectAnswers(attempt.getTotalQuestions())
                || certificates.findByAttemptId(attempt.getId()).isPresent()) {
            return;
        }

        Certificate certificate = new Certificate();
        certificate.setCertificateNumber("GEA-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        certificate.setUser(attempt.getUser());
        certificate.setCategory(attempt.getCategory());
        certificate.setHolderName(holderName(attempt));
        certificate.setDirection(attempt.getCategory().getName());
        certificate.setAttempt(attempt);
        certificate.setCorrectAnswers(attempt.getCorrectAnswers());
        certificate.setTotalQuestions(attempt.getTotalQuestions());
        certificate.setScore(attempt.getScore());
        certificates.save(certificate);
    }

    public static int passingCorrectAnswers(int questionCount) {
        return (int) Math.ceil(questionCount * 0.875d);
    }

    @Transactional(readOnly = true)
    public List<CertificateView> myCertificates(Long userId) {
        return certificates.findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(userId)
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<CertificateView> allMyCertificates(Long userId) {
        return certificates.findByUserIdOrderByIssuedAtDesc(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public CertificateView myCertificate(Long userId, String certificateNumber) {
        Certificate certificate = certificates.findByCertificateNumberAndUserId(certificateNumber, userId)
                .orElseThrow(() -> ApiException.notFound("CERTIFICATE_NOT_FOUND", "Certificate was not found"));
        return toView(certificate);
    }

    @Transactional(readOnly = true)
    public CertificateView verify(String certificateNumber) {
        Certificate certificate = certificates.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> ApiException.notFound("CERTIFICATE_NOT_FOUND", "Certificate was not found"));
        return toView(certificate);
    }

    @Transactional
    public void revokeForAttempt(Long attemptId) {
        certificates.findByAttemptId(attemptId).ifPresent(certificate -> {
            if (certificate.getRevokedAt() == null) certificate.setRevokedAt(Instant.now());
        });
    }

    private CertificateView toView(Certificate certificate) {
        return new CertificateView(certificate.getCertificateNumber(), certificate.getHolderName(),
                certificate.getDirection(), certificate.getCorrectAnswers(), certificate.getTotalQuestions(),
                certificate.getScore(), certificate.getIssuedAt(), certificate.getRevokedAt() == null);
    }

    private String holderName(TestAttempt attempt) {
        var user = attempt.getUser();
        String fullName = String.join(" ", Arrays.asList(user.getFirstName(), user.getLastName()).stream()
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.toList())).trim();
        if (!fullName.isBlank()) return fullName;
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname().trim();
        return "Foydalanuvchi";
    }
}
