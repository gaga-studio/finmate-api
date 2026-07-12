package com.gagastudio.finmate.goals;

public class MainGoalNotFoundException extends RuntimeException {
	public MainGoalNotFoundException() {
		super("No active main goal exists");
	}
}
