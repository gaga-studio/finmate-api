package com.gagastudio.finmate.goals;

public class GoalRequiredException extends RuntimeException {
	public GoalRequiredException() {
		super("Confirm a main goal before using this action");
	}
}
