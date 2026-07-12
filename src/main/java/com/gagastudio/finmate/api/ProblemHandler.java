package com.gagastudio.finmate.api;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.gagastudio.finmate.auth.DuplicateEmailException;
import com.gagastudio.finmate.auth.InvalidCredentialsException;

@RestControllerAdvice
public class ProblemHandler {
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validation(MethodArgumentNotValidException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setType(URI.create("https://api.finmate.kr/problems/validation-failed"));
		problem.setTitle("Validation failed");
		problem.setProperty("code", "VALIDATION_FAILED");
		return problem;
	}

	@ExceptionHandler(DuplicateEmailException.class)
	ProblemDetail duplicateEmail(DuplicateEmailException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setType(URI.create("https://api.finmate.kr/problems/duplicate-email"));
		problem.setTitle("Email already registered");
		problem.setProperty("code", "DUPLICATE_EMAIL");
		return problem;
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
		problem.setType(URI.create("https://api.finmate.kr/problems/invalid-credentials"));
		problem.setTitle("Authentication failed");
		problem.setProperty("code", "INVALID_CREDENTIALS");
		return problem;
	}
}
