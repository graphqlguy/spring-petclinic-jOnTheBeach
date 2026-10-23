package org.springframework.samples.petclinic.upstream;

/**
 * Thrown when an external service fails to answer, or answers with an error. The GraphQL
 * response then carries an error for the affected field and keeps the rest of the data.
 */
public class UpstreamException extends RuntimeException {

	private final String upstream;

	public UpstreamException(String upstream, String message) {
		super(message);
		this.upstream = upstream;
	}

	public UpstreamException(String upstream, Throwable cause) {
		super(cause.getMessage(), cause);
		this.upstream = upstream;
	}

	/**
	 * The name of the external service, in words a client can read.
	 */
	public String getUpstream() {
		return upstream;
	}

}
