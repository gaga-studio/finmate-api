package com.gagastudio.finmate.goals;

public class ActiveMainGoalException extends RuntimeException {
	public ActiveMainGoalException() {
		super("An active main goal already exists");
	}
}
