package com.poupito.api.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimiterTest {

	@Test
	void shouldAllowRequestsUpToTheDefaultLimit() {
		ApiRateLimiter limiter = new ApiRateLimiter(3, Duration.ofMinutes(1), 10, Duration.ofMinutes(1));

		assertThat(limiter.checkDefault("1.2.3.4")).isNull();
		assertThat(limiter.checkDefault("1.2.3.4")).isNull();
		assertThat(limiter.checkDefault("1.2.3.4")).isNull();
	}

	@Test
	void shouldReturnRetryAfter_whenDefaultLimitExceeded() {
		ApiRateLimiter limiter = new ApiRateLimiter(2, Duration.ofMinutes(1), 10, Duration.ofMinutes(1));
		limiter.checkDefault("1.2.3.4");
		limiter.checkDefault("1.2.3.4");

		assertThat(limiter.checkDefault("1.2.3.4")).isNotNull().isPositive();
	}

	@Test
	void shouldTrackKeysIndependently() {
		ApiRateLimiter limiter = new ApiRateLimiter(1, Duration.ofMinutes(1), 10, Duration.ofMinutes(1));
		limiter.checkDefault("1.2.3.4");

		assertThat(limiter.checkDefault("5.6.7.8")).isNull();
	}

	@Test
	void shouldTrackDefaultAndExpensiveRulesIndependently() {
		ApiRateLimiter limiter = new ApiRateLimiter(1, Duration.ofMinutes(1), 1, Duration.ofMinutes(1));
		limiter.checkDefault("1.2.3.4");

		assertThat(limiter.checkExpensive("1.2.3.4")).isNull();
	}

	@Test
	void shouldReturnRetryAfter_whenExpensiveLimitExceeded() {
		ApiRateLimiter limiter = new ApiRateLimiter(100, Duration.ofMinutes(1), 1, Duration.ofMinutes(1));
		limiter.checkExpensive("1.2.3.4");

		assertThat(limiter.checkExpensive("1.2.3.4")).isNotNull().isPositive();
	}

	@Test
	void shouldResetAfterWindow() throws InterruptedException {
		ApiRateLimiter limiter = new ApiRateLimiter(1, Duration.ofMillis(40), 10, Duration.ofMinutes(1));
		limiter.checkDefault("1.2.3.4");
		Thread.sleep(60);

		assertThat(limiter.checkDefault("1.2.3.4")).isNull();
	}

}
