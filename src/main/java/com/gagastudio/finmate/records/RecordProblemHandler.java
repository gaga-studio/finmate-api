package com.gagastudio.finmate.records;

import com.gagastudio.finmate.api.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RecordProblemHandler {
	private final ApiProblems problems;
	RecordProblemHandler(ApiProblems problems) { this.problems = problems; }
	@ExceptionHandler(InvalidRecordRangeException.class)
	ProblemDetail invalidRange(InvalidRecordRangeException exception, HttpServletRequest request) {
		return problems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), "VALIDATION_FAILED");
	}
}
