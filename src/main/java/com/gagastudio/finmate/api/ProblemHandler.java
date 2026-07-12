package com.gagastudio.finmate.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.gagastudio.finmate.auth.DuplicateEmailException;
import com.gagastudio.finmate.auth.InvalidCredentialsException;

@RestControllerAdvice
public class ProblemHandler {
	private final ApiProblems apiProblems;

	public ProblemHandler(ApiProblems apiProblems) {
		this.apiProblems = apiProblems;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
			.toList();
		return apiProblems.validation(request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail malformedJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
		return apiProblems.validation(request, List.of(Map.of("field", "body", "message", "Malformed JSON request body")));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	ProblemDetail duplicateEmail(DuplicateEmailException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.CONFLICT, "duplicate-email", "Email already registered",
			exception.getMessage(), "DUPLICATE_EMAIL");
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.UNAUTHORIZED, "invalid-credentials", "Authentication failed",
			exception.getMessage(), "INVALID_CREDENTIALS");
	}
}
