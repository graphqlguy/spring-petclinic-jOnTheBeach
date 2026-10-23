package org.springframework.samples.petclinic.upstream;

import java.time.Duration;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Shared setup of the REST services behind the GraphQL schema. A service written by hand
 * is an HTTP interface, imported with <code>@ImportHttpServices</code> into a group of
 * its own, and Spring Boot reads each group's base URL and timeouts from the properties
 * <code>spring.http.serviceclient.&lt;group&gt;.*</code>. A generated service is served
 * by {@link RestOperationFetcher}, with a client from {@link UpstreamClients}.
 */
@Configuration(proxyBeanMethods = false)
public class RestUpstreams {

	private static final Log logger = LogFactory.getLog(RestUpstreams.class);

	/**
	 * Calls an external service through its HTTP interface, and turns a failure into an
	 * {@link UpstreamException} that names the service. A failure covers an error status,
	 * a timeout and a connection that could not be opened.
	 * @param upstream the name of the service, in words a client can read
	 * @param call the call to the HTTP interface
	 */
	public static <T> T call(String upstream, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (RestClientException ex) {
			throw new UpstreamException(upstream, ex);
		}
	}

	/**
	 * Logs every request to a REST service with the status of the answer and the time it
	 * took, so a demo can show which calls reached the live services, and names the
	 * application in the User-Agent header, as the usage policy of Nominatim asks.
	 */
	@Bean
	RestClientHttpServiceGroupConfigurer upstreamRequestLogging(
			@Value("${petclinic.upstream.user-agent}") String userAgent) {
		ClientHttpRequestInterceptor logging = (request, body, execution) -> {
			long started = System.nanoTime();
			ClientHttpResponse response = execution.execute(request, body);
			logger.info("REST %s %s answered %d (%d ms)".formatted(request.getMethod(), request.getURI(),
					response.getStatusCode().value(), Duration.ofNanos(System.nanoTime() - started).toMillis()));
			return response;
		};
		return (groups) -> groups.forEachClient((group, builder) -> builder.requestInterceptor(logging)
			.defaultHeader(HttpHeaders.USER_AGENT, userAgent));
	}

}
