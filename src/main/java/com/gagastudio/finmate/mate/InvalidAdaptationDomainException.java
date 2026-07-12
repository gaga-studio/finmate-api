package com.gagastudio.finmate.mate;

class InvalidAdaptationDomainException extends RuntimeException {
	InvalidAdaptationDomainException() {
		super("The selected adaptation domain is not available for this routine");
	}
}
