package com.poupito.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

	private static final int MIN_LENGTH = 12;
	private static final int MAX_LENGTH = 100;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return false;
		}
		if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
			return false;
		}
		boolean hasUpper = false;
		boolean hasLower = false;
		boolean hasDigit = false;
		boolean hasSymbol = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isUpperCase(c)) {
				hasUpper = true;
			} else if (Character.isLowerCase(c)) {
				hasLower = true;
			} else if (Character.isDigit(c)) {
				hasDigit = true;
			} else if (!Character.isWhitespace(c)) {
				hasSymbol = true;
			}
		}
		return hasUpper && hasLower && hasDigit && hasSymbol;
	}

}
