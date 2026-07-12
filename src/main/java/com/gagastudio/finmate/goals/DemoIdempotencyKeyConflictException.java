package com.gagastudio.finmate.goals;

class DemoIdempotencyKeyConflictException extends RuntimeException {
	DemoIdempotencyKeyConflictException() {
		super("Idempotency-Key was already used for a different demo timeline request");
	}
}
