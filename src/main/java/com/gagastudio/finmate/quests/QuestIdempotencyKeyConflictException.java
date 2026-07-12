package com.gagastudio.finmate.quests;

class QuestIdempotencyKeyConflictException extends RuntimeException {
	QuestIdempotencyKeyConflictException() {
		super("Idempotency-Key was already used for a different quest completion request");
	}
}
