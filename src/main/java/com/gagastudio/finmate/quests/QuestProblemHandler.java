package com.gagastudio.finmate.quests;

import com.gagastudio.finmate.api.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class QuestProblemHandler {
	private final ApiProblems problems;
	QuestProblemHandler(ApiProblems problems) { this.problems = problems; }
	@ExceptionHandler(QuestNotFoundException.class)
	ProblemDetail missing(QuestNotFoundException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.NOT_FOUND, "quest-not-found", "Quest not found", exception.getMessage(), "NOT_FOUND");
	}
	@ExceptionHandler(InvalidQuestCommandException.class)
	ProblemDetail invalid(InvalidQuestCommandException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), "VALIDATION_FAILED");
	}
	@ExceptionHandler(QuestIdempotencyKeyConflictException.class)
	ProblemDetail idempotencyConflict(QuestIdempotencyKeyConflictException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.CONFLICT, "idempotency-key-reused", "Idempotency key conflict",
			exception.getMessage(), "IDEMPOTENCY_KEY_REUSED");
	}
}
