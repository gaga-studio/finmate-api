package com.gagastudio.finmate.rewards;

import com.gagastudio.finmate.api.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = RewardController.class)
class RewardProblemHandler {
	private final ApiProblems problems;

	RewardProblemHandler(ApiProblems problems) {
		this.problems = problems;
	}

	@ExceptionHandler(RewardException.class)
	ProblemDetail reward(RewardException exception, HttpServletRequest request) {
		HttpStatus status = "NOT_FOUND".equals(exception.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
		return problems.create(request, status, "reward-command-failed", "Reward command failed",
			exception.getMessage(), exception.getCode());
	}
}
