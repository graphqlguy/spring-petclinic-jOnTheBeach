package org.springframework.samples.petclinic.upstream;

/**
 * Thrown when a client argument is rejected before it reaches an external service. The
 * message is shown to the client, so it must be safe to read.
 */
public class InvalidArgumentException extends RuntimeException {

	public InvalidArgumentException(String message) {
		super(message);
	}

}
