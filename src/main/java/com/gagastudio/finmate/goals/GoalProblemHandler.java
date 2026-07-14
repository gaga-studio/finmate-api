package com.gagastudio.finmate.goals;

import com.gagastudio.finmate.api.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GoalProblemHandler {
	private final ApiProblems apiProblems;

	GoalProblemHandler(ApiProblems apiProblems) {
		this.apiProblems = apiProblems;
	}

	@ExceptionHandler(InvalidMainGoalException.class)
	ProblemDetail invalidGoal(InvalidMainGoalException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
			exception.getMessage(), "VALIDATION_FAILED");
	}

	@ExceptionHandler(InvalidReportMonthException.class)
	ProblemDetail invalidReportMonth(InvalidReportMonthException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed",
			exception.getMessage(), "VALIDATION_FAILED");
	}

	@ExceptionHandler(ActiveMainGoalException.class)
	ProblemDetail activeGoal(ActiveMainGoalException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.CONFLICT, "active-main-goal-exists", "Active main goal exists",
			exception.getMessage(), "ACTIVE_MAIN_GOAL_EXISTS");
	}

	@ExceptionHandler(MainGoalNotFoundException.class)
	ProblemDetail missingGoal(MainGoalNotFoundException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.NOT_FOUND, "main-goal-not-found", "Main goal not found",
			exception.getMessage(), "NOT_FOUND");
	}

	@ExceptionHandler(GoalRequiredException.class)
	ProblemDetail goalRequired(GoalRequiredException exception, HttpServletRequest request) {
		return apiProblems.create(request, HttpStatus.CONFLICT, "goal-required", "Goal required",
			exception.getMessage(), "GOAL_REQUIRED");
	}
}
