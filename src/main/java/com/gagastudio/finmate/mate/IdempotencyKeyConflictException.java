package com.gagastudio.finmate.mate;

class IdempotencyKeyConflictException extends RuntimeException {
	IdempotencyKeyConflictException() {
		super("Idempotency-Key was already used for a different request");
	}
}
