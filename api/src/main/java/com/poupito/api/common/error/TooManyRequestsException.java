package com.poupito.api.common.error;

public class TooManyRequestsException extends RuntimeException {

	/** Segundos até liberar; null quando não aplicável (o header Retry-After é omitido). */
	private final Long retryAfterSeconds;

	public TooManyRequestsException() {
		super("Muitas tentativas. Aguarde alguns instantes e tente novamente.");
		this.retryAfterSeconds = null;
	}

	public TooManyRequestsException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
