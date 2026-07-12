package com.gagastudio.finmate.goals;

public class InvalidReportMonthException extends RuntimeException {
	public InvalidReportMonthException() {
		super("month must use YYYY-MM");
	}
}
