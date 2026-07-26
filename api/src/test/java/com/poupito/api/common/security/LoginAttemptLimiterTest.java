package com.poupito.api.common.security;

import com.poupito.api.common.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

	@Test
	void shouldNotLock_beforeReachingMaxFailures() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(5));
		limiter.recordFailure("a@poupito.com");
		limiter.recordFailure("a@poupito.com");

		assertThatCode(() -> limiter.checkNotLocked("a@poupito.com")).doesNotThrowAnyException();
	}

	@Test
	void shouldLock_afterReachingMaxFailures() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(5));
		limiter.recordFailure("a@poupito.com");
		limiter.recordFailure("a@poupito.com");
		limiter.recordFailure("a@poupito.com");

		assertThatThrownBy(() -> limiter.checkNotLocked("A@Poupito.com"))
				.isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void shouldExposeRetryAfterSeconds_whenLocked() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, Duration.ofMinutes(5));
		limiter.recordFailure("a@poupito.com");

		assertThatThrownBy(() -> limiter.checkNotLocked("a@poupito.com"))
				.isInstanceOfSatisfying(TooManyRequestsException.class,
						ex -> assertThat(ex.getRetryAfterSeconds()).isNotNull().isPositive());
	}

	@Test
	void shouldResetOnSuccess() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, Duration.ofMinutes(5));
		limiter.recordFailure("a@poupito.com");
		limiter.recordFailure("a@poupito.com");
		limiter.reset("a@poupito.com");

		assertThatCode(() -> limiter.checkNotLocked("a@poupito.com")).doesNotThrowAnyException();
	}

	@Test
	void shouldTrackAccountsIndependently() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, Duration.ofMinutes(5));
		limiter.recordFailure("a@poupito.com");

		assertThatCode(() -> limiter.checkNotLocked("b@poupito.com")).doesNotThrowAnyException();
	}

	@Test
	void shouldUnlock_afterWindowExpires() throws InterruptedException {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(1, Duration.ofMillis(40));
		limiter.recordFailure("a@poupito.com");
		Thread.sleep(60);

		assertThatCode(() -> limiter.checkNotLocked("a@poupito.com")).doesNotThrowAnyException();
	}

	@Test
	void shouldStartFreshWindow_afterOldFailuresExpire() throws InterruptedException {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, Duration.ofMillis(40));
		limiter.recordFailure("a@poupito.com");
		Thread.sleep(60);
		limiter.recordFailure("a@poupito.com");

		assertThatCode(() -> limiter.checkNotLocked("a@poupito.com")).doesNotThrowAnyException();
	}

}
