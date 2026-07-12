package com.gagastudio.finmate.records;

class InvalidRecordRangeException extends RuntimeException {
	InvalidRecordRangeException() { super("Record range must be inclusive, ordered, and no longer than 31 days"); }
}
