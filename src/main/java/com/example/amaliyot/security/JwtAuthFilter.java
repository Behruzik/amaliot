package com.example.amaliyot.security;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.repository.RevokedTokenRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final RevokedTokenRepository revokedTokens;

    public JwtAuthFilter(JwtService jwtService, UserAccountRepository users, RevokedTokenRepository revokedTokens) {
        this.jwtService = jwtService; this.users = users; this.revokedTokens = revokedTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.Claims claims = jwtService.parse(header.substring(7).trim());
                UserAccount user = revokedTokens.existsById(claims.tokenId()) ? null : users.findById(claims.userId()).orElse(null);
                if (user != null && user.isActive() && user.getRole().name().equals(claims.role())
                        && user.getTokenVersion() == claims.tokenVersion()) {
                    var principal = new AuthPrincipal(user.getId(), user.getRole());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
