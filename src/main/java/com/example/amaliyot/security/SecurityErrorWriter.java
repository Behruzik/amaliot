package com.example.amaliyot.security;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Error responses produced before MVC, including anonymous and throttled calls. */
final class SecurityErrorWriter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SecurityErrorWriter() {}

    static void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        // Write a stable ISO timestamp without relying on MVC date configuration.
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());
        JSON.writeValue(response.getOutputStream(), body);
    }
}
