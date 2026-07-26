package com.poupito.api.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ApiError.validation(fields);
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
		return ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiError handleInvalidCredentials(InvalidCredentialsException ex) {
		return ApiError.of(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiError handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		return ApiError.of(HttpStatus.UNAUTHORIZED.value(), ex.getMessage());
	}

	@ExceptionHandler(TooManyRequestsException.class)
	public org.springframework.http.ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
		ApiError body = ApiError.of(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage());
		org.springframework.http.ResponseEntity.BodyBuilder builder =
				org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
		if (ex.getRetryAfterSeconds() != null) {
			builder.header(org.springframework.http.HttpHeaders.RETRY_AFTER,
					String.valueOf(ex.getRetryAfterSeconds()));
		}
		return builder.body(body);
	}

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError handleNotFound(NotFoundException ex) {
		return ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());
	}

	@ExceptionHandler(DuplicateResourceException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError handleDuplicateResource(DuplicateResourceException ex) {
		return ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage());
	}

	@ExceptionHandler(BusinessException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError handleBusiness(BusinessException ex) {
		return ApiError.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
	}

	@ExceptionHandler(ExternalServiceException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ApiError handleExternalService(ExternalServiceException ex) {
		log.warn("Falha ao chamar serviço externo", ex);
		return ApiError.of(HttpStatus.BAD_GATEWAY.value(), ex.getMessage());
	}

	@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
		return ApiError.of(HttpStatus.CONFLICT.value(),
				"Registro em uso por outros dados (ex.: transações) e não pode ser alterado/excluído");
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiError handleUnexpected(Exception ex) {
		log.error("Erro não tratado", ex);
		return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Erro interno do servidor");
	}

}
