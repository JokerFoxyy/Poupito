package com.poupito.api.common.security;

import com.poupito.api.common.error.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter de janela fixa, in-memory, para endpoints de autenticação.
 * Suficiente para deploy single-instance (Lightsail/EC2). Multi-instância exigirá
 * um store compartilhado (ex.: Redis).
 */
@Component
public class LoginRateLimiter {

	private static final Logger SECURITY_LOG = LoggerFactory.getLogger("com.poupito.api.security");

	private final int maxAttempts;
	private final Duration window;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public LoginRateLimiter(
			@Value("${app.security.rate-limit.max-attempts:10}") int maxAttempts,
			@Value("${app.security.rate-limit.window:PT1M}") Duration window) {
		this.maxAttempts = maxAttempts;
		this.window = window;
	}

	/** Registra uma tentativa para a chave; lança 429 se o limite da janela foi excedido. */
	public void check(String key) {
		Instant now = Instant.now();
		Window updated = windows.compute(key, (k, existing) -> {
			if (existing == null || now.isAfter(existing.resetAt())) {
				return new Window(1, now.plus(window));
			}
			return new Window(existing.count() + 1, existing.resetAt());
		});
		if (updated.count() > maxAttempts) {
			MDC.put("event", "login_rate_limit_exceeded");
			try {
				SECURITY_LOG.warn("Rate limit de login excedido");
			} finally {
				MDC.clear();
			}
			throw new TooManyRequestsException();
		}
	}

	private record Window(int count, Instant resetAt) {
	}

}
