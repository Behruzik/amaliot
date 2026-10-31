package com.example.amaliyot.service;

import com.example.amaliyot.domain.ContentType;
import com.example.amaliyot.domain.Entities.AuditLog;
import com.example.amaliyot.domain.Entities.ManagedContent;
import com.example.amaliyot.repository.AuditLogRepository;
import com.example.amaliyot.repository.ManagedContentRepository;
import com.example.amaliyot.web.ApiDtos.ManagedContentRequest;
import com.example.amaliyot.web.ApiDtos.ManagedContentView;
import com.example.amaliyot.web.ApiDtos.MessageView;
import com.example.amaliyot.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ManagedContentService {
    private final ManagedContentRepository content;
    private final AuditLogRepository audit;

    public ManagedContentService(ManagedContentRepository content, AuditLogRepository audit) {
        this.content = content;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ManagedContentView get(ContentType type) {
        return content.findByType(type).map(ManagedContentService::toView)
                .orElseGet(() -> new ManagedContentView(type, "", null));
    }

    @Transactional(readOnly = true)
    public List<ManagedContentView> list() {
        return content.findAll().stream().sorted(Comparator.comparing(item -> item.getType().name()))
                .map(ManagedContentService::toView).toList();
    }

    @Transactional
    public ManagedContentView upsert(ContentType type, ManagedContentRequest request, Long actorId) {
        ManagedContent item = content.findByType(type).orElseGet(ManagedContent::new);
        boolean created = item.getId() == null;
        item.setType(type);
        item.setContent(request.content().trim());
        item.setUpdatedById(actorId);
        item.setUpdatedAt(Instant.now());
        content.save(item);
        audit.save(new AuditLog(actorId, created ? "CONTENT_CREATED" : "CONTENT_UPDATED", "CONTENT", item.getId(), type.name()));
        return toView(item);
    }

    @Transactional
    public MessageView delete(ContentType type, Long actorId) {
        ManagedContent item = content.findByType(type)
                .orElseThrow(() -> ApiException.notFound("CONTENT_NOT_FOUND", "Managed content was not found"));
        audit.save(new AuditLog(actorId, "CONTENT_DELETED", "CONTENT", item.getId(), type.name()));
        content.delete(item);
        return new MessageView("Content deleted");
    }

    private static ManagedContentView toView(ManagedContent item) {
        return new ManagedContentView(item.getType(), item.getContent(), item.getUpdatedAt());
    }
}
