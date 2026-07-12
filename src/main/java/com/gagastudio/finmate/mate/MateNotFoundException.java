package com.gagastudio.finmate.mate;

class MateNotFoundException extends RuntimeException {
	MateNotFoundException() {
		super("Mate resource was not found");
	}
}
