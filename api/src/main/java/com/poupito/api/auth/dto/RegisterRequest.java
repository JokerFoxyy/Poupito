package com.poupito.api.auth.dto;

import com.poupito.api.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @StrongPassword String password) {
}
