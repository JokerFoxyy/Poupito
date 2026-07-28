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
 * Lockout de login <b>por conta</b>: após N falhas de senha dentro da janela, a conta fica
 * bloqueada pela duração da janela (default 5 falhas → 5 min). Conta apenas <b>falhas</b> e é
 * <b>zerado no login bem-sucedido</b> — semântica diferente do {@link LoginRateLimiter} (janela
 * fixa por IP+email, anti-burst, conta toda tentativa). Os dois coexistem (defesa em profundidade).
 *
 * <p>In-memory, adequado para deploy single-instance (Lightsail). Multi-instância exigirá um store
 * compartilhado (ex.: Redis), mesma ressalva do {@link LoginRateLimiter}.
 */
@Component
public class LoginAttemptLimiter {

	private static final Logger SECURITY_LOG = LoggerFactory.getLogger("com.poupito.api.security");

	private final int maxFailures;
	private final Duration window;
	private final Map<String, State> states = new ConcurrentHashMap<>();

	public LoginAttemptLimiter(
			@Value("${app.security.lockout.max-failures:5}") int maxFailures,
			@Value("${app.security.lockout.window:PT5M}") Duration window) {
		this.maxFailures = maxFailures;
		this.window = window;
	}

	/** Lança 429 (com Retry-After em segundos) se a conta estiver bloqueada agora. */
	public void checkNotLocked(String email) {
		State state = states.get(key(email));
		Instant now = Instant.now();
		if (state != null && state.lockedUntil() != null && now.isBefore(state.lockedUntil())) {
			long seconds = Math.max(1, Duration.between(now, state.lockedUntil()).getSeconds());
			logSecurityEvent("login_blocked_locked_account");
			throw new TooManyRequestsException(
					"Muitas tentativas de login. Tente novamente em alguns minutos.", seconds);
		}
	}

	/** Registra uma falha de login; ao atingir o limite, bloqueia a conta pela duração da janela. */
	public void recordFailure(String email) {
		Instant now = Instant.now();
		State updated = states.compute(key(email), (k, existing) -> {
			boolean fresh = existing == null || now.isAfter(existing.windowEnd());
			int failures = fresh ? 1 : existing.failures() + 1;
			Instant windowEnd = fresh ? now.plus(window) : existing.windowEnd();
			Instant lockedUntil = failures >= maxFailures ? now.plus(window)
					: (fresh ? null : existing.lockedUntil());
			return new State(failures, windowEnd, lockedUntil);
		});
		if (updated.lockedUntil() != null && updated.failures() == maxFailures) {
			logSecurityEvent("login_lockout_triggered");
		}
	}

	private void logSecurityEvent(String event) {
		MDC.put("event", event);
		try {
			SECURITY_LOG.warn("Evento de segurança em login: {}", event);
		} finally {
			MDC.clear();
		}
	}

	/** Zera o estado da conta (login bem-sucedido). */
	public void reset(String email) {
		states.remove(key(email));
	}

	private String key(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

	private record State(int failures, Instant windowEnd, Instant lockedUntil) {
	}

}
