package com.gagastudio.finmate.goals;

class DemoTimelineStaleException extends RuntimeException {
	DemoTimelineStaleException() { super("Expected demo timeline stage is stale"); }
}
