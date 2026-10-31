package com.example.amaliyot.domain;

import jakarta.persistence.*;
import com.example.amaliyot.domain.Difficulty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Entities {
    private Entities() {}

    @Entity(name = "UserAccount")
    @Table(name = "app_users", indexes = @Index(name = "ix_app_users_role", columnList = "role"))
    @Getter @Setter @NoArgsConstructor
    public static class UserAccount {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(unique = true, length = 32)
        private String phone;
        @Column(unique = true, length = 254)
        private String email;
        @Column(length = 50)
        private String nickname;
        @Column(name = "password_hash", nullable = false, length = 100)
        private String passwordHash;
        @Column(name = "password_salt", length = 18)
        private String passwordSalt;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "profession_category_id")
        private Category professionCategory;
        @Column(name = "token_version", nullable = false)
        private long tokenVersion;
        @Column(name = "first_name", length = 100)
        private String firstName;
        @Column(name = "last_name", length = 100)
        private String lastName;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
        private Role role = Role.USER;
        @Column(length = 16)
        private String gender;
        @Column(name = "offer_accepted", nullable = false)
        private boolean offerAccepted;
        @Column(name = "birth_date")
        private LocalDate birthDate;
        @Column(length = 120)
        private String region;
        @Column(length = 120)
        private String district;
        @Column(length = 500)
        private String address;
        @Column(name = "avatar_url", length = 500)
        private String avatarUrl;
        @Column(nullable = false)
        private boolean active = true;
        @Column(name = "active_since")
        private Instant activeSince;
        @Column(name = "active_seconds_total", nullable = false)
        private long activeSecondsTotal;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        public UserAccount(String phone, String passwordHash, String firstName, String lastName, boolean offerAccepted) {
            this.phone = phone;
            this.passwordHash = passwordHash;
            this.firstName = firstName;
            this.lastName = lastName;
            this.offerAccepted = offerAccepted;
            this.role = Role.USER;
        }

        public void setActive(boolean active) {
            Instant now = Instant.now();
            if (this.active && !active && activeSince != null) {
                activeSecondsTotal += Math.max(0, java.time.Duration.between(activeSince, now).getSeconds());
                activeSince = null;
            } else if (!this.active && active) {
                activeSince = now;
            }
            this.active = active;
        }

        @PrePersist void onCreate() {
            createdAt = updatedAt = Instant.now();
            if (active && activeSince == null) activeSince = createdAt;
        }
        @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "categories", indexes = @Index(name = "ix_categories_active", columnList = "active"))
    @Getter @Setter @NoArgsConstructor
    public static class Category {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(nullable = false, unique = true, length = 160)
        private String name;
        @Column(length = 2000)
        private String description;
        @Column(name = "image_url", length = 500)
        private String imageUrl;
        @Column(nullable = false)
        private boolean active = true;
        @Column(name = "duration_minutes", nullable = false)
        private int durationMinutes;
        @Column(name = "question_count", nullable = false)
        private int questionCount;
        @Column(name = "additional_question_count", nullable = false)
        private int additionalQuestionCount;
        @Column(name = "retake_delay_hours", nullable = false)
        private int retakeDelayHours;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "created_by_id")
        private UserAccount createdBy;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "updated_by_id")
        private UserAccount updatedBy;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "deleted_by_id")
        private UserAccount deletedBy;
        @Column(name = "deleted_at")
        private Instant deletedAt;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    }

    @Entity(name = "GeographicRegion")
    @Table(name = "geo_regions", uniqueConstraints =
            @UniqueConstraint(name = "uq_geo_regions_name", columnNames = "name"))
    @Getter @Setter @NoArgsConstructor
    public static class GeographicRegion {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(nullable = false, length = 120)
        private String name;
        @Column(nullable = false)
        private boolean active = true;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    }

    @Entity(name = "GeographicDistrict")
    @Table(name = "geo_districts", uniqueConstraints =
            @UniqueConstraint(name = "uq_geo_districts_region_name", columnNames = {"region_id", "name"}),
            indexes = @Index(name = "ix_geo_districts_region_active", columnList = "region_id,active"))
    @Getter @Setter @NoArgsConstructor
    public static class GeographicDistrict {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "region_id", nullable = false)
        private GeographicRegion region;
        @Column(nullable = false, length = 120)
        private String name;
        @Column(nullable = false)
        private boolean active = true;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    }

    @Entity(name = "ManagedContent")
    @Table(name = "managed_content", uniqueConstraints =
            @UniqueConstraint(name = "uq_managed_content_type", columnNames = "content_type"))
    @Getter @Setter @NoArgsConstructor
    public static class ManagedContent {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Enumerated(EnumType.STRING) @Column(name = "content_type", nullable = false, length = 24)
        private ContentType type;
        @Column(nullable = false, length = 20000)
        private String content;
        @Column(name = "updated_by_id")
        private Long updatedById;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist @PreUpdate void onSave() { updatedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "questions", indexes = {
            @Index(name = "ix_questions_category_active", columnList = "category_id,active"),
            @Index(name = "ix_questions_type", columnList = "answer_type")
    })
    @Getter @Setter @NoArgsConstructor
    public static class Question {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "category_id")
        private Category category;
        @Column(nullable = false, length = 5000)
        private String body;
        @Column(name = "image_url", length = 500)
        private String imageUrl;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
        private Difficulty difficulty = Difficulty.MEDIUM;
        @Column(name = "global_pool", nullable = false)
        private boolean globalPool;
        @Enumerated(EnumType.STRING) @Column(name = "answer_type", nullable = false, length = 16)
        private AnswerType answerType;
        @Column(name = "numeric_answer", precision = 20, scale = 6)
        private BigDecimal numericAnswer;
        @Column(name = "numeric_tolerance", precision = 20, scale = 6)
        private BigDecimal numericTolerance = BigDecimal.ZERO;
        @Column(nullable = false)
        private boolean active = true;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "author_id")
        private UserAccount author;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "editor_id")
        private UserAccount editor;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;
        @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<AnswerOption> options = new ArrayList<>();

        public void replaceOptions(List<AnswerOption> replacement) {
            options.clear();
            for (AnswerOption option : replacement) {
                option.setQuestion(this);
                options.add(option);
            }
        }
        @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
        @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "answer_options", indexes = @Index(name = "ix_options_question", columnList = "question_id"))
    @Getter @Setter @NoArgsConstructor
    public static class AnswerOption {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "question_id", nullable = false)
        private Question question;
        @Column(length = 2000)
        private String label;
        @Column(name = "image_url", length = 500)
        private String imageUrl;
        @Column(name = "is_correct", nullable = false)
        private boolean correct;
    }

    @Entity
    @Table(name = "test_attempts", indexes = {
            @Index(name = "ix_attempt_user_category", columnList = "user_id,category_id,created_at"),
            @Index(name = "ix_attempt_status_created", columnList = "status,created_at"),
            @Index(name = "ix_attempt_archived_created", columnList = "archived_at,created_at")
    })
    @Getter @Setter @NoArgsConstructor
    public static class TestAttempt {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private UserAccount user;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "category_id", nullable = false)
        private Category category;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
        private AttemptStatus status = AttemptStatus.PENDING;
        @Column(name = "started_at", nullable = false)
        private Instant startedAt;
        @Column(name = "deadline_at", nullable = false)
        private Instant deadlineAt;
        @Column(name = "completed_at")
        private Instant completedAt;
        @Column(name = "archived_at")
        private Instant archivedAt;
        @Column(name = "saved_at")
        private Instant savedAt;
        @Column(name = "total_questions", nullable = false)
        private int totalQuestions;
        @Column(name = "correct_answers", nullable = false)
        private int correctAnswers;
        @Column(precision = 7, scale = 2, nullable = false)
        private BigDecimal score = BigDecimal.ZERO;
        @Version @Column(nullable = false)
        private long version;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("displayOrder ASC")
        private Set<AttemptQuestion> questions = new LinkedHashSet<>();
        @PrePersist void onCreate() { createdAt = Instant.now(); }
    }

    @Entity
    @Table(name = "attempt_questions", indexes = @Index(name = "ix_attempt_questions_attempt", columnList = "attempt_id,display_order"))
    @Getter @Setter @NoArgsConstructor
    public static class AttemptQuestion {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "attempt_id", nullable = false)
        private TestAttempt attempt;
        @Column(name = "source_question_id")
        private Long sourceQuestionId;
        @Column(name = "source_category_id", nullable = false)
        private Long sourceCategoryId;
        @Column(nullable = false, length = 5000)
        private String body;
        @Column(name = "image_url", length = 500)
        private String imageUrl;
        @Enumerated(EnumType.STRING) @Column(name = "answer_type", nullable = false, length = 16)
        private AnswerType answerType;
        @Column(name = "numeric_answer", precision = 20, scale = 6)
        private BigDecimal numericAnswer;
        @Column(name = "numeric_tolerance", precision = 20, scale = 6)
        private BigDecimal numericTolerance = BigDecimal.ZERO;
        @Column(name = "display_order", nullable = false)
        private int displayOrder;
        @OneToMany(mappedBy = "attemptQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<AttemptOption> options = new ArrayList<>();
    }

    @Entity
    @Table(name = "attempt_options", indexes = @Index(name = "ix_attempt_options_question", columnList = "attempt_question_id"))
    @Getter @Setter @NoArgsConstructor
    public static class AttemptOption {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "attempt_question_id", nullable = false)
        private AttemptQuestion attemptQuestion;
        @Column(length = 2000)
        private String label;
        @Column(name = "image_url", length = 500)
        private String imageUrl;
        @Column(name = "is_correct", nullable = false)
        private boolean correct;
    }

    @Entity
    @Table(name = "attempt_answers", uniqueConstraints = @UniqueConstraint(name = "uq_attempt_answer_question", columnNames = "attempt_question_id"))
    @Getter @Setter @NoArgsConstructor
    public static class AttemptAnswer {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "attempt_question_id", nullable = false)
        private AttemptQuestion attemptQuestion;
        @ElementCollection
        @CollectionTable(name = "attempt_answer_options", joinColumns = @JoinColumn(name = "answer_id"))
        @Column(name = "option_id", nullable = false)
        private Set<Long> selectedOptionIds = new HashSet<>();
        @Column(name = "numeric_value", precision = 20, scale = 6)
        private BigDecimal numericValue;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;
        @PrePersist @PreUpdate void onSave() { updatedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "retake_grants", indexes = @Index(name = "ix_retake_grant_lookup", columnList = "user_id,category_id,consumed"))
    @Getter @Setter @NoArgsConstructor
    public static class RetakeGrant {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
        private UserAccount user;
        @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)
        private Category category;
        @Column(name = "granted_by_id", nullable = false)
        private Long grantedById;
        @Column(nullable = false)
        private boolean consumed;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @PrePersist void onCreate() { createdAt = Instant.now(); }
    }

    @Entity
    @Table(name = "audit_logs", indexes = @Index(name = "ix_audit_target", columnList = "target_type,target_id,created_at"))
    @Getter @Setter @NoArgsConstructor
    public static class AuditLog {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "actor_id", nullable = false)
        private Long actorId;
        @Column(nullable = false, length = 80)
        private String action;
        @Column(name = "target_type", nullable = false, length = 60)
        private String targetType;
        @Column(name = "target_id", nullable = false)
        private Long targetId;
        @Column(length = 1000)
        private String details;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        public AuditLog(Long actorId, String action, String targetType, Long targetId, String details) {
            this.actorId = actorId; this.action = action; this.targetType = targetType;
            this.targetId = targetId; this.details = details;
        }
        @PrePersist void onCreate() { createdAt = Instant.now(); }
    }

    @Entity
    @Table(name = "revoked_tokens", indexes = @Index(name = "ix_revoked_tokens_expiry", columnList = "expires_at"))
    @Getter @Setter @NoArgsConstructor
    public static class RevokedToken {
        @Id @Column(name = "token_id", length = 64)
        private String tokenId;
        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;
        @Column(name = "revoked_at", nullable = false, updatable = false)
        private Instant revokedAt;
        public RevokedToken(String tokenId, Instant expiresAt) {
            this.tokenId = tokenId; this.expiresAt = expiresAt;
        }
        @PrePersist void onCreate() { revokedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "certificates", indexes = @Index(name = "ix_certificates_user_issued", columnList = "user_id,issued_at"))
    @Getter @Setter @NoArgsConstructor
    public static class Certificate {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "certificate_number", nullable = false, unique = true, length = 40)
        private String certificateNumber;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private UserAccount user;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "category_id", nullable = false)
        private Category category;
        @Column(name = "holder_name", nullable = false, length = 250)
        private String holderName;
        @Column(name = "direction_name", nullable = false, length = 160)
        private String direction;
        @OneToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "attempt_id", nullable = false, unique = true)
        private TestAttempt attempt;
        @Column(name = "correct_answers", nullable = false)
        private int correctAnswers;
        @Column(name = "total_questions", nullable = false)
        private int totalQuestions;
        @Column(precision = 7, scale = 2, nullable = false)
        private BigDecimal score;
        @Column(name = "issued_at", nullable = false, updatable = false)
        private Instant issuedAt;
        @Column(name = "revoked_at")
        private Instant revokedAt;

        @PrePersist void onCreate() { issuedAt = Instant.now(); }
    }

    @Entity
    @Table(name = "support_requests", indexes = {
            @Index(name = "ix_support_requests_created", columnList = "created_at"),
            @Index(name = "ix_support_requests_status", columnList = "status")
    })
    @Getter @Setter @NoArgsConstructor
    public static class SupportRequest {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private UserAccount user;
        @Column(name = "nickname_snapshot", length = 50)
        private String nickname;
        @Column(nullable = false, length = 32)
        private String phone;
        @Column(name = "request_type", nullable = false, length = 24)
        private String type;
        @Column(nullable = false, length = 200)
        private String subject;
        @Column(nullable = false, length = 5000)
        private String message;
        @Column(nullable = false, length = 24)
        private String status = "OPEN";
        @Column(name = "staff_reply", length = 3000)
        private String staffReply;
        @Column(name = "updated_by_id")
        private Long updatedById;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;
        @PrePersist @PreUpdate void onSave() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; updatedAt = now; }
    }
}
