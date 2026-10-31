package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Entities.Category;
import com.example.amaliyot.repository.CategoryRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.repository.TestAttemptRepository;
import com.example.amaliyot.service.CertificateService;
import com.example.amaliyot.web.ApiDtos.ProfileUpdateRequest;
import com.example.amaliyot.web.ApiDtos.UserView;
import com.example.amaliyot.web.ApiDtos.UserDashboardView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.Duration;
import java.util.TreeSet;
import java.util.List;

@Service
public class ProfileService {
    private final UserAccountRepository users;
    private final LocationCatalogService locations;
    private final TestAttemptRepository attempts;
    private final CertificateService certificates;
    private final CategoryRepository categories;
    public ProfileService(UserAccountRepository users, LocationCatalogService locations,
                          TestAttemptRepository attempts, CertificateService certificates, CategoryRepository categories) {
        this.users = users;
        this.locations = locations;
        this.attempts = attempts;
        this.certificates = certificates;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public UserView get(Long id) { return AuthService.toView(find(id)); }

    @Transactional(readOnly = true)
    public UserDashboardView dashboard(Long id) {
        UserAccount user = find(id);
        Instant now = Instant.now();
        long activeSeconds = user.getActiveSecondsTotal();
        if (user.isActive() && user.getActiveSince() != null)
            activeSeconds += Math.max(0, Duration.between(user.getActiveSince(), now).getSeconds());
        var userAttempts = attempts.findByUserId(id);
        var completed = userAttempts.stream().filter(a -> a.getStatus() != com.example.amaliyot.domain.AttemptStatus.IN_PROGRESS).toList();
        long passed = completed.stream().filter(a -> a.getTotalQuestions() == CertificateService.EXAM_QUESTION_COUNT
                && a.getCorrectAnswers() >= CertificateService.PASSING_CORRECT_ANSWERS
                && a.getStatus() != com.example.amaliyot.domain.AttemptStatus.CANCELLED
                && a.getStatus() != com.example.amaliyot.domain.AttemptStatus.EXPIRED).count();
        var directions = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        completed.forEach(a -> directions.add(a.getCategory().getName()));
        return new UserDashboardView(AuthService.toView(user), user.getCreatedAt(), activeSeconds,
                completed.size(), passed, completed.size() - passed, List.copyOf(directions), certificates.allMyCertificates(id));
    }

    @Transactional
    public UserView update(Long id, ProfileUpdateRequest request) {
        UserAccount user = findForUpdate(id);
        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("INVALID_BIRTH_DATE", "Birth date cannot be in the future");
        }
        if (request.region() != null || request.district() != null) {
            locations.validateSelection(request.region() == null ? user.getRegion() : request.region(),
                    request.district() == null ? user.getDistrict() : request.district());
        }
        if (request.firstName() != null) user.setFirstName(request.firstName().trim());
        if (request.lastName() != null) user.setLastName(request.lastName().trim());
        if (request.phone() != null) {
            String phone = AuthService.normalizePhone(request.phone());
            if (AuthService.phoneTaken(users, phone, id)) throw ApiException.conflict("PHONE_ALREADY_REGISTERED", "This phone number is already registered");
            user.setPhone(phone);
        }
        if (request.nickname() != null) user.setNickname(request.nickname().isBlank() ? null : request.nickname().trim());
        if (request.gender() != null) user.setGender(request.gender().isBlank() ? null : request.gender());
        if (request.birthDate() != null) user.setBirthDate(request.birthDate());
        if (request.region() != null) user.setRegion(request.region().trim());
        if (request.district() != null) user.setDistrict(request.district().trim());
        if (request.address() != null) user.setAddress(request.address().trim());
        if (Boolean.TRUE.equals(request.offerAccepted())) user.setOfferAccepted(true);
        if (request.professionId() != null) {
            Category profession = categories.findById(request.professionId())
                    .filter(Category::isActive)
                    .orElseThrow(() -> ApiException.notFound("PROFESSION_NOT_FOUND", "Active profession was not found"));
            user.setProfessionCategory(profession);
        }
        return AuthService.toView(user);
    }

    @Transactional
    public UserView updateAvatar(Long id, String avatarUrl) {
        UserAccount user = findForUpdate(id);
        user.setAvatarUrl(avatarUrl);
        return AuthService.toView(user);
    }

    private UserAccount find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
    }

    private UserAccount findForUpdate(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found"));
    }
}
