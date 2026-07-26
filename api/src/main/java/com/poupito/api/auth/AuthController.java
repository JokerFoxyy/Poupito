package com.poupito.api.auth;

import com.poupito.api.auth.dto.ForgotPasswordRequest;
import com.poupito.api.auth.dto.IssuedTokens;
import com.poupito.api.auth.dto.LoginRequest;
import com.poupito.api.auth.dto.RegisterRequest;
import com.poupito.api.auth.dto.ResetPasswordRequest;
import com.poupito.api.auth.dto.UserResponse;
import com.poupito.api.auth.refresh.RefreshTokenService;
import com.poupito.api.common.error.InvalidCredentialsException;
import com.poupito.api.common.security.AuthCookieFactory;
import com.poupito.api.common.security.AuthenticatedUser;
import com.poupito.api.common.security.LoginAttemptLimiter;
import com.poupito.api.common.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthCookieFactory cookieFactory;
	private final LoginRateLimiter rateLimiter;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public AuthController(AuthService authService, AuthCookieFactory cookieFactory,
			LoginRateLimiter rateLimiter, LoginAttemptLimiter loginAttemptLimiter,
			JwtService jwtService, RefreshTokenService refreshTokenService) {
		this.authService = authService;
		this.cookieFactory = cookieFactory;
		this.rateLimiter = rateLimiter;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest) {
		rateLimiter.check(rateKey(httpRequest, request.email()));
		IssuedTokens tokens = authService.register(request.email(), request.password());
		return withSessionCookies(HttpStatus.CREATED, tokens);
	}

	@PostMapping("/login")
	public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest) {
		rateLimiter.check(rateKey(httpRequest, request.email()));
		loginAttemptLimiter.checkNotLocked(request.email());
		try {
			IssuedTokens tokens = authService.login(request.email(), request.password());
			loginAttemptLimiter.reset(request.email());
			return withSessionCookies(HttpStatus.OK, tokens);
		} catch (InvalidCredentialsException e) {
			loginAttemptLimiter.recordFailure(request.email());
			throw e;
		}
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
			HttpServletRequest httpRequest) {
		rateLimiter.check(rateKey(httpRequest, request.email()));
		authService.forgotPassword(request.email());
	}

	@PostMapping("/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request.token(), request.newPassword());
	}

	@PostMapping("/refresh")
	public ResponseEntity<UserResponse> refresh(
			@CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
		IssuedTokens tokens = authService.refresh(refreshToken);
		return withSessionCookies(HttpStatus.OK, tokens);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccess().toString())
				.header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefresh().toString())
				.build();
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return new UserResponse(user.id(), user.email());
	}

	private ResponseEntity<UserResponse> withSessionCookies(HttpStatus status, IssuedTokens tokens) {
		ResponseCookie access = cookieFactory.access(tokens.accessToken(), jwtService.expiration());
		ResponseCookie refresh = cookieFactory.refresh(tokens.refreshToken(), refreshTokenService.ttl());
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE, access.toString())
				.header(HttpHeaders.SET_COOKIE, refresh.toString())
				.body(tokens.user());
	}

	private String rateKey(HttpServletRequest request, String email) {
		return request.getRemoteAddr() + ":" + (email == null ? "" : email.trim().toLowerCase());
	}

}
