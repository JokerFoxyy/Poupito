package com.poupito.api.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/** Custo do BCrypt: 12 é apropriado para dados financeiros (mais lento contra brute-force offline). */
	private static final int BCRYPT_STRENGTH = 12;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
			ApiRateLimitFilter apiRateLimitFilter) throws Exception {
		return http
				// CSRF: a defesa é o cookie SameSite=Strict (não vai em requisição cross-site) + API
				// JSON same-origin. Sem cookies de sessão de servidor, o token CSRF clássico não se aplica.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.frameOptions(frame -> frame.deny())
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31_536_000)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/v1/auth/register", "/v1/auth/login",
								"/v1/auth/refresh", "/v1/auth/logout",
								"/v1/auth/forgot-password", "/v1/auth/reset-password").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				// rate limit roda antes do JWT: protege também os endpoints públicos (login/register)
				.addFilterBefore(apiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

	@Bean
	ApiRateLimitFilter apiRateLimitFilter(ApiRateLimiter apiRateLimiter,
			com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
		return new ApiRateLimitFilter(apiRateLimiter, objectMapper);
	}

}
