package com.gagastudio.finmate.mate;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.gagastudio.finmate.api.ApiProblems;

@RestControllerAdvice
class MateProblemHandler {
	private final ApiProblems problems;

	MateProblemHandler(ApiProblems problems) {
		this.problems = problems;
	}

	@ExceptionHandler(MateNotFoundException.class)
	ProblemDetail notFound(MateNotFoundException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.NOT_FOUND, "not-found", "Not found", exception.getMessage(), "NOT_FOUND");
	}

	@ExceptionHandler(ActiveRoutineBuildException.class)
	ProblemDetail activeBuild(ActiveRoutineBuildException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.CONFLICT, "active-routine-build-exists", "Active routine build exists",
			exception.getMessage(), "ACTIVE_ROUTINE_BUILD_EXISTS");
	}

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	ProblemDetail idempotencyConflict(IdempotencyKeyConflictException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.CONFLICT, "idempotency-key-reused", "Idempotency key conflict",
			exception.getMessage(), "IDEMPOTENCY_KEY_REUSED");
	}

	@ExceptionHandler({InvalidAdaptationDomainException.class, InvalidRoutineBuildRequestException.class})
	ProblemDetail invalidRequest(RuntimeException exception, HttpServletRequest request) {
		String code = exception instanceof InvalidAdaptationDomainException ? "ADAPTATION_DOMAIN_REQUIRED" : "VALIDATION_FAILED";
		return problems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), code);
	}
}
