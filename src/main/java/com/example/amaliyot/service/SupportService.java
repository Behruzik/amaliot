package com.example.amaliyot.service;

import com.example.amaliyot.domain.Entities.SupportRequest;
import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import com.example.amaliyot.repository.SupportRequestRepository;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.web.ApiDtos.SupportRequestCreate;
import com.example.amaliyot.web.ApiDtos.SupportRequestUpdate;
import com.example.amaliyot.web.ApiDtos.SupportRequestView;
import com.example.amaliyot.web.ApiDtos.SupportContactView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class SupportService {
    private final SupportRequestRepository requests;
    private final UserAccountRepository users;
    public SupportService(SupportRequestRepository requests, UserAccountRepository users) { this.requests = requests; this.users = users; }

    @Transactional
    public SupportRequestView create(Long userId, SupportRequestCreate input) {
        UserAccount user = user(userId);
        String phone = AuthService.normalizePhone(input.phone() == null || input.phone().isBlank() ? user.getPhone() : input.phone());
        String type = input.type().trim().toUpperCase(Locale.ROOT);
        SupportRequest request = new SupportRequest(); request.setUser(user); request.setNickname(user.getNickname());
        request.setPhone(phone); request.setType(type); request.setSubject(input.subject().trim()); request.setMessage(input.message().trim());
        return view(requests.save(request));
    }

    @Transactional(readOnly = true)
    public List<SupportRequestView> mine(Long userId) {
        return requests.findByUserIdOrderByCreatedAtDesc(userId).stream().map(SupportService::view).toList();
    }

    @Transactional(readOnly = true)
    public SupportContactView contact(String fallbackPhone) {
        String phone = users.findFirstByRole(Role.SUPER_ADMIN).map(UserAccount::getPhone).filter(p -> p != null && !p.isBlank())
                .orElse(fallbackPhone);
        phone = formatContactPhone(phone);
        return new SupportContactView(phone, List.of("PHONE_CALL", "APPLICATION", "FEEDBACK"));
    }

    @Transactional(readOnly = true)
    public List<SupportRequestView> list(String search, String status) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String wantedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        return requests.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> wantedStatus == null || r.getStatus().equals(wantedStatus))
                .filter(r -> term.isEmpty() || contains(r.getNickname(), term) || contains(r.getUser().getEmail(), term)
                        || contains(r.getPhone(), term))
                .map(SupportService::view).toList();
    }

    @Transactional
    public SupportRequestView update(Long id, SupportRequestUpdate input, Long actorId) {
        SupportRequest request = requests.findById(id).orElseThrow(() -> ApiException.notFound("SUPPORT_REQUEST_NOT_FOUND", "Support request was not found"));
        request.setStatus(input.status().trim().toUpperCase(Locale.ROOT));
        request.setStaffReply(input.staffReply() == null || input.staffReply().isBlank() ? null : input.staffReply().trim());
        request.setUpdatedById(actorId);
        return view(request);
    }

    private UserAccount user(Long id) { return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User was not found")); }
    private static boolean contains(String value, String term) { return value != null && value.toLowerCase(Locale.ROOT).contains(term); }
    private static String formatContactPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 9) return digits.substring(0, 2) + " " + digits.substring(2, 5) + " " + digits.substring(5, 7) + " " + digits.substring(7);
        if (digits.length() == 12 && digits.startsWith("998")) return "+998 " + digits.substring(3, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8, 10) + " " + digits.substring(10);
        return phone;
    }
    private static SupportRequestView view(SupportRequest r) {
        return new SupportRequestView(r.getId(), r.getUser().getId(), r.getNickname(), r.getUser().getEmail(), r.getPhone(),
                r.getType(), r.getSubject(), r.getMessage(), r.getStatus(), r.getStaffReply(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
