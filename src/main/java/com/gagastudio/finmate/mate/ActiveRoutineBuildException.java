package com.gagastudio.finmate.mate;

class ActiveRoutineBuildException extends RuntimeException {
	ActiveRoutineBuildException() {
		super("An active routine build already exists");
	}
}
