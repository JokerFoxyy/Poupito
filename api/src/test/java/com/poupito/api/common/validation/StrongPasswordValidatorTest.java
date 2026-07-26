package com.poupito.api.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

	private final StrongPasswordValidator validator = new StrongPasswordValidator();

	private boolean valid(String value) {
		return validator.isValid(value, null);
	}

	@Test
	void shouldAccept_whenPasswordMeetsAllRules() {
		assertThat(valid("NovaSenha-2026")).isTrue();
	}

	@Test
	void shouldReject_whenNull() {
		assertThat(valid(null)).isFalse();
	}

	@Test
	void shouldReject_whenTooShort() {
		assertThat(valid("Ab1!xyz")).isFalse();
	}

	@Test
	void shouldReject_whenTooLong() {
		assertThat(valid("Aa1!".repeat(30))).isFalse();
	}

	@Test
	void shouldReject_whenMissingUppercase() {
		assertThat(valid("novasenha-2026")).isFalse();
	}

	@Test
	void shouldReject_whenMissingLowercase() {
		assertThat(valid("NOVASENHA-2026")).isFalse();
	}

	@Test
	void shouldReject_whenMissingDigit() {
		assertThat(valid("NovaSenha-Poup")).isFalse();
	}

	@Test
	void shouldReject_whenMissingSymbol() {
		assertThat(valid("NovaSenha2026x")).isFalse();
	}

}
