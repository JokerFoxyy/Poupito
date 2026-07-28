package com.poupito.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter de janela fixa, in-memory, por regra nomeada + chave (normalmente o IP
 * do cliente). Suficiente para deploy single-instance (Lightsail); multi-instância
 * exigiria um store compartilhado (ex.: Redis). Duas regras hoje: "default" (toda a
 * API) e "expensive" (export/import), configuráveis via properties.
 */
@Component
public class ApiRateLimiter {

	private final int defaultMaxRequests;
	private final Duration defaultWindow;
	private final int expensiveMaxRequests;
	private final Duration expensiveWindow;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public ApiRateLimiter(
			@Value("${app.security.api-rate-limit.default.max-requests:120}") int defaultMaxRequests,
			@Value("${app.security.api-rate-limit.default.window:PT1M}") Duration defaultWindow,
			@Value("${app.security.api-rate-limit.expensive.max-requests:10}") int expensiveMaxRequests,
			@Value("${app.security.api-rate-limit.expensive.window:PT1M}") Duration expensiveWindow) {
		this.defaultMaxRequests = defaultMaxRequests;
		this.defaultWindow = defaultWindow;
		this.expensiveMaxRequests = expensiveMaxRequests;
		this.expensiveWindow = expensiveWindow;
	}

	/** Registra uma requisição da regra "default" para a chave; retorna segundos até resetar se estourou, senão null. */
	public Long checkDefault(String key) {
		return check("default:" + key, defaultMaxRequests, defaultWindow);
	}

	/** Mesma coisa, mas com o limite mais agressivo usado em endpoints caros (export/import). */
	public Long checkExpensive(String key) {
		return check("expensive:" + key, expensiveMaxRequests, expensiveWindow);
	}

	private Long check(String windowKey, int maxRequests, Duration window) {
		Instant now = Instant.now();
		Window updated = windows.compute(windowKey, (k, existing) -> {
			if (existing == null || now.isAfter(existing.resetAt())) {
				return new Window(1, now.plus(window));
			}
			return new Window(existing.count() + 1, existing.resetAt());
		});
		if (updated.count() > maxRequests) {
			return Duration.between(now, updated.resetAt()).getSeconds() + 1;
		}
		return null;
	}

	private record Window(int count, Instant resetAt) {
	}

}
