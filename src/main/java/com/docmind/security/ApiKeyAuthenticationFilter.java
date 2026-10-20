package com.docmind.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates requests carrying a known API key. Requests without a valid key
 * pass through unauthenticated and are rejected later by the authorization rules.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String PRINCIPAL = "api-client";

    private final String headerName;
    private final List<byte[]> validKeys;

    public ApiKeyAuthenticationFilter(ApiKeyProperties properties) {
        this.headerName = properties.headerName();
        this.validKeys = properties.apiKeys().stream()
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(headerName);
        if (StringUtils.hasText(presented) && isValid(presented)) {
            var authentication = new PreAuthenticatedAuthenticationToken(
                    PRINCIPAL, null, AuthorityUtils.createAuthorityList("ROLE_API"));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private boolean isValid(String presented) {
        byte[] candidate = presented.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        for (byte[] key : validKeys) {
            // constant-time comparison, and no early exit to avoid leaking which key matched
            match |= MessageDigest.isEqual(key, candidate);
        }
        return match;
    }
}
