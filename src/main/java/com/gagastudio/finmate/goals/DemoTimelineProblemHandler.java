package com.gagastudio.finmate.goals;

import com.gagastudio.finmate.api.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class DemoTimelineProblemHandler {
	private final ApiProblems problems;
	DemoTimelineProblemHandler(ApiProblems problems) { this.problems = problems; }
	@ExceptionHandler(DemoTimelineStaleException.class)
	ProblemDetail stale(DemoTimelineStaleException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.CONFLICT, "data-stale", "Data stale", exception.getMessage(), "DATA_STALE");
	}
	@ExceptionHandler(InvalidDemoTimelineException.class)
	ProblemDetail invalid(InvalidDemoTimelineException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), "VALIDATION_FAILED");
	}
}
