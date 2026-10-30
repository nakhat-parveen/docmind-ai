package com.docmind.ratelimit;

import com.docmind.security.ApiKeyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the per-key limit to {@code POST /api/ask} before the controller runs,
 * so a flood never reaches the embedding or chat model.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ApiKeyProperties apiKeyProperties;
    private final ApiKeyRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimitProperties properties,
                                ApiKeyProperties apiKeyProperties,
                                ApiKeyRateLimiter limiter,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.apiKeyProperties = apiKeyProperties;
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.enabled()) {
            return true;
        }
        String apiKey = request.getHeader(apiKeyProperties.headerName());
        if (!StringUtils.hasText(apiKey)) {
            return true;
        }
        ApiKeyRateLimiter.Decision decision = limiter.tryConsume(apiKey);
        if (decision.allowed()) {
            return true;
        }
        long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Too many questions for this API key, try again later");
        problem.setTitle("Too many requests");
        objectMapper.writeValue(response.getOutputStream(), problem);
        return false;
    }
}
