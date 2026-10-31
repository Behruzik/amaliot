package com.example.amaliyot.service;

import com.example.amaliyot.domain.AnswerType;
import com.example.amaliyot.domain.AttemptStatus;
import com.example.amaliyot.domain.Entities.*;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.*;
import com.example.amaliyot.web.ApiDtos.*;
import com.example.amaliyot.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class AdminService {
    private final UserAccountRepository users;
    private final CategoryRepository categories;
    private final QuestionRepository questions;
    private final TestAttemptRepository attempts;
    private final AttemptAnswerRepository answers;
    private final RetakeGrantRepository retakeGrants;
    private final AuditLogRepository audit;
    private final PasswordStorageService passwords;
    private final AttemptService attemptService;
    private final CertificateService certificates;

    public AdminService(UserAccountRepository users, CategoryRepository categories, QuestionRepository questions,
                        TestAttemptRepository attempts, AttemptAnswerRepository answers, RetakeGrantRepository retakeGrants,
                        AuditLogRepository audit, PasswordStorageService passwords, AttemptService attemptService,
                        CertificateService certificates) {
        this.users = users; this.categories = categories; this.questions = questions; this.attempts = attempts;
        this.answers = answers; this.retakeGrants = retakeGrants; this.audit = audit;
        this.passwords = passwords; this.attemptService = attemptService;
        this.certificates = certificates;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        return new DashboardView(users.count(), users.countByActiveTrue(), categories.findByActiveTrueOrderByNameAsc().size(),
                questions.count(), attempts.countByStatus(AttemptStatus.PENDING), attempts.countByStatus(AttemptStatus.APPROVED),
                attempts.countByStatus(AttemptStatus.CANCELLED), attempts.countByStatus(AttemptStatus.EXPIRED));
    }

    @Transactional(readOnly = true)
    public Page<UserView> users(String search, Role role, Boolean active, int page, int size) {
        Specification<UserAccount> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("phone")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("nickname")), like),
                        cb.like(cb.lower(root.get("firstName")), like), cb.like(cb.lower(root.get("lastName")), like)));
            }
            if (role != null) predicates.add(cb.equal(root.get("role"), role));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return users.findAll(spec, pageRequest(page, size, "id")).map(AuthService::toView);
    }

    @Transactional
    public UserView createUser(AdminUserRequest request, Long actorId) {
        UserAccount actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Administrator was not found"));
        requireUserManager(actor);
        if (request.role() == Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_ROLE_RESERVED", "The Super Admin role can only be assigned by the system data loader");
        if (request.role() == Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_REQUIRED", "Only the Super Admin can assign the Admin role");
        String email = AuthService.normalizeGmail(request.email());
          String phone = AuthService.normalizePhone(request.phone());
        AuthService.validateNewPassword(request.password());
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "This Gmail address is already registered");
          if (AuthService.phoneTaken(users, phone, null)) throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered");
        UserAccount user = new UserAccount(null, "", clean(request.firstName()), clean(request.lastName()), false);
        passwords.setPassword(user, request.password());
          user.setEmail(email); user.setPhone(phone); user.setNickname(clean(request.nickname()));
        user.setRole(request.role()); user.setActive(request.active());
        users.save(user);
        log(actorId, "USER_CREATED", "USER", user.getId(), "role=" + user.getRole());
        return AuthService.toView(user);
    }

    @Transactional
    public UserView setUserActive(Long userId, boolean active, Long actorId) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        UserAccount actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Administrator was not found"));
        requireUserManager(actor);
        if (user.getRole() == Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "The Super Admin account cannot be changed here");
        if (!active && userId.equals(actorId) && user.getRole() == Role.ADMIN)
            throw ApiException.conflict("CANNOT_DISABLE_SELF", "An administrator cannot remove their own administrator access");
        if (!active && actor.getRole() != Role.SUPER_ADMIN && user.getRole() == Role.ADMIN
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1)
            throw ApiException.conflict("LAST_ADMIN", "At least one active administrator must remain");
        user.setActive(active); log(actorId, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", userId, null);
        return AuthService.toView(user);
    }

    @Transactional
    public UserView updateUser(Long userId, AdminUserUpdateRequest request, Long actorId) {
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
        UserAccount actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Administrator was not found"));
        requireUserManager(actor);
        if (user.getRole() == Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "The Super Admin account cannot be changed here");
        Role nextRole = request.role() == null ? user.getRole() : request.role();
        boolean nextActive = request.active() == null ? user.isActive() : request.active();
        if (nextRole == Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_ROLE_RESERVED", "The Super Admin role can only be assigned by the system data loader");
        if (nextRole == Role.ADMIN && user.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN)
            throw ApiException.forbidden("SUPER_ADMIN_REQUIRED", "Only the Super Admin can assign the Admin role");
        if (userId.equals(actorId) && (nextRole != Role.ADMIN || !nextActive))
            throw ApiException.conflict("CANNOT_DISABLE_SELF", "An administrator cannot remove their own administrator access");
        if (actor.getRole() != Role.SUPER_ADMIN && user.getRole() == Role.ADMIN && (nextRole != Role.ADMIN || !nextActive)
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1)
            throw ApiException.conflict("LAST_ADMIN", "At least one active administrator must remain");
        if (request.firstName() != null) user.setFirstName(clean(request.firstName()));
        if (request.lastName() != null) user.setLastName(clean(request.lastName()));
          if (request.phone() != null) {
              String phone = AuthService.normalizePhone(request.phone());
              if (AuthService.phoneTaken(users, phone, userId)) throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered");
              user.setPhone(phone);
          }
        if (request.nickname() != null) user.setNickname(clean(request.nickname()));
        user.setRole(nextRole); user.setActive(nextActive);
        log(actorId, "USER_UPDATED", "USER", userId, "role=" + nextRole + ";active=" + nextActive);
        return AuthService.toView(user);
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories() { return categories.findAll(Sort.by("name")).stream().map(AdminService::toView).toList(); }

    @Transactional
    public CategoryView createCategory(CategoryRequest request, Long actorId) {
        Category category = new Category(); apply(category, request); categories.save(category);
        log(actorId, "CATEGORY_CREATED", "CATEGORY", category.getId(), null);
        return toView(category);
    }

    @Transactional
    public CategoryView updateCategory(Long id, CategoryRequest request, Long actorId) {
        Category category = findCategory(id); apply(category, request);
        log(actorId, "CATEGORY_UPDATED", "CATEGORY", id, null);
        return toView(category);
    }

    @Transactional
    public void archiveCategory(Long id, Long actorId) {
        Category category = findCategory(id); category.setActive(false);
        log(actorId, "CATEGORY_DEACTIVATED", "CATEGORY", id, null);
    }

    @Transactional(readOnly = true)
    public Page<QuestionView> questions(Long categoryId, String search, int page, int size) {
        Specification<Question> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (search != null && !search.isBlank()) predicates.add(cb.like(cb.lower(root.get("body")), "%" + search.trim().toLowerCase(Locale.ROOT) + "%"));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return questions.findAll(spec, pageRequest(page, size, "id")).map(this::toQuestionView);
    }

    @Transactional(readOnly = true)
    public QuestionView question(Long id) { return toQuestionView(findQuestion(id)); }

    @Transactional
    public QuestionView createQuestion(QuestionRequest request, Long actorId) {
        Question question = new Question(); question.setAuthor(findUser(actorId)); question.setEditor(findUser(actorId));
        apply(question, request); questions.save(question);
        log(actorId, "QUESTION_CREATED", "QUESTION", question.getId(), null);
        return toQuestionView(question);
    }

    @Transactional
    public QuestionView updateQuestion(Long id, QuestionRequest request, Long actorId) {
        Question question = findQuestion(id); question.setEditor(findUser(actorId)); apply(question, request);
        log(actorId, "QUESTION_UPDATED", "QUESTION", id, null);
        // New options on an already managed question receive their generated IDs
        // at flush time. Return usable IDs in this response, not null placeholders.
        questions.flush();
        return toQuestionView(question);
    }

    @Transactional
    public QuestionView setQuestionImage(Long id, String imageUrl, Long actorId) {
        Question question = findQuestion(id); question.setImageUrl(imageUrl); question.setEditor(findUser(actorId));
        log(actorId, "QUESTION_IMAGE_UPDATED", "QUESTION", id, null);
        return toQuestionView(question);
    }

    @Transactional
    public void archiveQuestion(Long id, Long actorId) {
        Question question = findQuestion(id); question.setActive(false); question.setEditor(findUser(actorId));
        log(actorId, "QUESTION_DEACTIVATED", "QUESTION", id, null);
    }

    private void apply(Question question, QuestionRequest request) {
        Category category = findCategory(request.categoryId());
        String difficulty = request.difficulty().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw ApiException.badRequest("INVALID_DIFFICULTY", "Difficulty must be EASY, MEDIUM, or HARD");
        question.setCategory(category); question.setBody(request.body().trim());
        question.setImageUrl(clean(request.imageUrl())); question.setDifficulty(difficulty); question.setAnswerType(request.answerType());
        if (request.answerType() == AnswerType.NUMERIC) {
            if (request.numericAnswer() == null) throw ApiException.badRequest("NUMERIC_ANSWER_REQUIRED", "Numeric questions need an expected value");
            if (request.options() != null && !request.options().isEmpty()) throw ApiException.badRequest("INVALID_OPTIONS", "Numeric questions cannot have choice options");
            if (!AttemptService.fitsNumericColumn(request.numericAnswer())
                    || (request.numericTolerance() != null && (!AttemptService.fitsNumericColumn(request.numericTolerance())
                    || request.numericTolerance().signum() < 0)))
                throw ApiException.badRequest("INVALID_NUMERIC_ANSWER", "Numeric values support at most 14 integer digits and 6 decimal places; tolerance must be nonnegative");
            question.setNumericAnswer(request.numericAnswer());
            question.setNumericTolerance(request.numericTolerance() == null ? BigDecimal.ZERO : request.numericTolerance());
            question.replaceOptions(List.of());
        } else {
            List<OptionInput> inputs = request.options() == null ? List.of() : request.options();
            if (inputs.size() < 2) throw ApiException.badRequest("OPTIONS_REQUIRED", "Choice questions need at least two options");
            long correctCount = inputs.stream().filter(OptionInput::correct).count();
            if (request.answerType() == AnswerType.SINGLE && correctCount != 1) throw ApiException.badRequest("INVALID_CORRECT_OPTIONS", "Single-answer questions need exactly one correct option");
            if (request.answerType() == AnswerType.MULTIPLE && correctCount < 1) throw ApiException.badRequest("INVALID_CORRECT_OPTIONS", "Multiple-answer questions need at least one correct option");
            List<AnswerOption> options = new ArrayList<>();
            for (OptionInput input : inputs) {
                String label = clean(input.label()); String image = clean(input.imageUrl());
                if (label == null && image == null) throw ApiException.badRequest("EMPTY_OPTION", "Each option needs text or an image");
                AnswerOption option = new AnswerOption(); option.setLabel(label); option.setImageUrl(image); option.setCorrect(input.correct());
                options.add(option);
            }
            question.setNumericAnswer(null); question.setNumericTolerance(BigDecimal.ZERO); question.replaceOptions(options);
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminAttemptView> attempts(String search, Long categoryId, AttemptStatus status,
                                           boolean archived, int page, int size) {
        Specification<TestAttempt> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var user = root.join("user");
                predicates.add(cb.or(cb.like(cb.lower(user.get("phone")), like),
                        cb.like(cb.lower(user.get("email")), like),
                        cb.like(cb.lower(user.get("nickname")), like),
                        cb.like(cb.lower(user.get("firstName")), like), cb.like(cb.lower(user.get("lastName")), like)));
            }
            query.distinct(true);
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return attempts.findAll(spec, pageRequest(page, size, "createdAt"))
                .map(a -> adminView(attempts.findWithSnapshotById(a.getId()).orElse(a)));
    }

    @Transactional(readOnly = true)
    public AdminAttemptView attempt(Long id) {
        TestAttempt attempt = attempts.findWithSnapshotById(id).orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt was not found"));
        return adminView(attempt);
    }

    private AdminAttemptView adminView(TestAttempt attempt) {
        Map<Long, AttemptAnswer> submitted = answers.findByAttemptQuestionAttemptId(attempt.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(answer -> answer.getAttemptQuestion().getId(), answer -> answer));
        List<AdminQuestionResult> details = attempt.getQuestions().stream().sorted(Comparator.comparingInt(AttemptQuestion::getDisplayOrder)).map(question -> {
            AttemptAnswer answer = submitted.get(question.getId());
            Set<Long> selected = answer == null || answer.getSelectedOptionIds() == null ? Set.of() : answer.getSelectedOptionIds();
            List<OptionView> options = question.getOptions().stream().map(o -> new OptionView(o.getId(), o.getLabel(), o.getImageUrl(), o.isCorrect())).toList();
            boolean correct = false;
            if (question.getAnswerType() == AnswerType.NUMERIC) {
                if (answer != null && answer.getNumericValue() != null && question.getNumericAnswer() != null) {
                    BigDecimal tolerance = question.getNumericTolerance() == null ? BigDecimal.ZERO : question.getNumericTolerance();
                    correct = answer.getNumericValue().subtract(question.getNumericAnswer()).abs().compareTo(tolerance) <= 0;
                }
            } else {
                Set<Long> expected = question.getOptions().stream().filter(AttemptOption::isCorrect).map(AttemptOption::getId).collect(java.util.stream.Collectors.toSet());
                correct = !expected.isEmpty() && expected.equals(selected);
            }
            return new AdminQuestionResult(question.getId(), question.getDisplayOrder(), question.getSourceCategoryId(),
                    question.getBody(), question.getAnswerType(), options, selected, question.getNumericAnswer(),
                    answer == null ? null : answer.getNumericValue(), correct);
        }).toList();
        UserAccount user = attempt.getUser();
        String name = String.join(" ", Arrays.asList(user.getFirstName(), user.getLastName()).stream().filter(Objects::nonNull).toList()).trim();
        return new AdminAttemptView(attemptService.resultForAdmin(attempt), user.getPhone(), user.getEmail(),
                user.getNickname(), name, details,
                attempt.getArchivedAt());
    }

    @Transactional
    public AdminAttemptView archiveAttempt(Long id, boolean archive, Long actorId) {
        TestAttempt attempt = lockedAttempt(id);
        if (attempt.getCompletedAt() == null)
            throw ApiException.conflict("ATTEMPT_NOT_FINISHED", "An active attempt cannot be archived");

        if (archive && attempt.getArchivedAt() == null) {
            attempt.setArchivedAt(java.time.Instant.now());
            log(actorId, "ATTEMPT_ARCHIVED", "ATTEMPT", id, null);
        } else if (!archive && attempt.getArchivedAt() != null) {
            attempt.setArchivedAt(null);
            log(actorId, "ATTEMPT_RESTORED", "ATTEMPT", id, null);
        }
        return adminView(attempt);
    }

    @Transactional
    public AdminAttemptView changeAttemptStatus(Long id, AttemptStatus next, Long actorId) {
        if (next != AttemptStatus.APPROVED && next != AttemptStatus.CANCELLED)
            throw ApiException.badRequest("INVALID_ATTEMPT_STATUS", "The result can only be approved or cancelled");
        TestAttempt attempt = lockedAttempt(id);
        if (attempt.getCompletedAt() == null || attempt.getStatus() != AttemptStatus.PENDING)
            throw ApiException.conflict("ATTEMPT_STATUS_CONFLICT", "Only completed pending attempts can be approved or cancelled");
        if (next == AttemptStatus.CANCELLED) certificates.revokeForAttempt(id);
        attempt.setStatus(next); log(actorId, "ATTEMPT_" + next.name(), "ATTEMPT", id, null);
        return adminView(attempt);
    }

    @Transactional
    public MessageView grantRetake(GrantRetakeRequest request, Long actorId) {
        UserAccount user = findUser(request.userId()); Category category = findCategory(request.categoryId());
        RetakeGrant grant = new RetakeGrant(); grant.setUser(user); grant.setCategory(category); grant.setGrantedById(actorId);
        retakeGrants.save(grant); log(actorId, "RETAKE_GRANTED", "USER", user.getId(), "categoryId=" + category.getId());
        return new MessageView("Retake permission granted");
    }

    private Category findCategory(Long id) { return categories.findById(id).orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category was not found")); }
    private TestAttempt lockedAttempt(Long id) {
        TestAttempt locked = attempts.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt was not found"));
        return attempts.findWithSnapshotById(id).orElse(locked);
    }
    private Question findQuestion(Long id) { return questions.findWithOptionsById(id).orElseThrow(() -> ApiException.notFound("QUESTION_NOT_FOUND", "Question was not found")); }
    private UserAccount findUser(Long id) { return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found")); }
    private static void requireUserManager(UserAccount actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN)
            throw ApiException.forbidden("ADMIN_REQUIRED", "Only Admin or Super Admin can create, update, or deactivate staff accounts");
    }
    private void log(Long actorId, String action, String type, Long id, String details) { audit.save(new AuditLog(actorId, action, type, id, details)); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static PageRequest pageRequest(int page, int size, String sort) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, sort));
    }
    private static void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim()); category.setDescription(clean(request.description()));
        category.setImageUrl(clean(request.imageUrl())); category.setActive(request.active());
        category.setDurationMinutes(request.durationMinutes()); category.setQuestionCount(request.questionCount());
        category.setAdditionalQuestionCount(request.additionalQuestionCount()); category.setRetakeDelayHours(request.retakeDelayHours());
    }
    private static CategoryView toView(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getDescription(), category.getImageUrl(),
                category.isActive(), category.getDurationMinutes(), category.getQuestionCount(),
                category.getAdditionalQuestionCount(), category.getRetakeDelayHours());
    }
    private QuestionView toQuestionView(Question question) {
        return new QuestionView(question.getId(), question.getCategory().getId(), question.getBody(), question.getImageUrl(),
                question.getDifficulty(), question.getAnswerType(), question.getNumericAnswer(), question.getNumericTolerance(),
                question.isActive(), question.getOptions().stream().map(o -> new OptionView(o.getId(), o.getLabel(), o.getImageUrl(), o.isCorrect())).toList(),
                question.getAuthor() == null ? null : question.getAuthor().getId(), question.getEditor() == null ? null : question.getEditor().getId());
    }
}
