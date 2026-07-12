package com.gagastudio.finmate.goals;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GoalProblemHandler {
	@ExceptionHandler(InvalidMainGoalException.class)
	ProblemDetail invalidGoal(InvalidMainGoalException exception) {
		return problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), "VALIDATION_FAILED");
	}

	@ExceptionHandler(InvalidReportMonthException.class)
	ProblemDetail invalidReportMonth(InvalidReportMonthException exception) {
		return problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed", exception.getMessage(), "VALIDATION_FAILED");
	}

	@ExceptionHandler(ActiveMainGoalException.class)
	ProblemDetail activeGoal(ActiveMainGoalException exception) {
		return problem(HttpStatus.CONFLICT, "active-main-goal-exists", "Active main goal exists", exception.getMessage(), "ACTIVE_MAIN_GOAL_EXISTS");
	}

	@ExceptionHandler(MainGoalNotFoundException.class)
	ProblemDetail missingGoal(MainGoalNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "main-goal-not-found", "Main goal not found", exception.getMessage(), "MAIN_GOAL_NOT_FOUND");
	}

	private ProblemDetail problem(HttpStatus status, String type, String title, String detail, String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("https://api.finmate.kr/problems/" + type));
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}
}
