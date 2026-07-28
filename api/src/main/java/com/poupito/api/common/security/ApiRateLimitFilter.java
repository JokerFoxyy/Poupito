package com.poupito.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poupito.api.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting geral da API — cobre todo endpoint (inclusive os públicos de auth),
 * não só login/registro (isso já era feito pelo {@link LoginRateLimiter}). Endpoints
 * caros (export/import) têm um limite bem mais agressivo. Roda antes do
 * {@link JwtAuthFilter} na cadeia de segurança, então nem precisa autenticar pra ser
 * limitado — protege também tentativas anônimas de exaustão.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

	private static final Logger SECURITY_LOG = LoggerFactory.getLogger("com.poupito.api.security");

	private final ApiRateLimiter rateLimiter;
	private final ObjectMapper objectMapper;

	public ApiRateLimitFilter(ApiRateLimiter rateLimiter, ObjectMapper objectMapper) {
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
	}

	private boolean isExpensive(String path) {
		return path.startsWith("/v1/transactions/export") || path.startsWith("/v1/import");
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getServletPath().startsWith("/actuator/health");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// getServletPath() (não getRequestURI()) já exclui o context-path (/api) — o
		// mesmo espaço de path usado pelos requestMatchers do SecurityConfig.
		String path = request.getServletPath();
		String ip = clientIp(request);

		Long retryAfter = isExpensive(path) ? rateLimiter.checkExpensive(ip) : rateLimiter.checkDefault(ip);
		if (retryAfter != null) {
			MDC.put("event", "rate_limit_exceeded");
			MDC.put("ip", ip);
			MDC.put("path", path);
			try {
				SECURITY_LOG.warn("Rate limit excedido");
			} finally {
				MDC.clear();
			}
			writeTooManyRequests(response, retryAfter);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
		response.setStatus(429);
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiError body = ApiError.of(429, "Muitas requisições. Tente novamente em instantes.");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}

	private String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

}
