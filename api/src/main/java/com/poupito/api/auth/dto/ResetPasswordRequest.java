package com.poupito.api.auth.dto;

import com.poupito.api.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
		@NotBlank String token,
		@NotBlank @StrongPassword String newPassword) {
}
