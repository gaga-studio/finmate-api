package com.gagastudio.finmate.auth;

public class InvalidCredentialsException extends RuntimeException {
	public InvalidCredentialsException() {
		super("The email or password is incorrect");
	}
}
