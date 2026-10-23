package org.springframework.samples.petclinic.upstream;

import java.time.Duration;

import graphql.schema.idl.RuntimeWiring;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the clients of the services whose fields a {@link RestOperationFetcher} serves.
 * Each client has the timeouts of the external services, which are shorter than the ones
 * the chat model needs, and a User-Agent that names the application, as the usage
 * policies of several services ask. It asks for JSON, which some services send only when
 * asked.
 */
@Component
public class UpstreamClients {

	private final ObjectProvider<RestClient.Builder> builders;

	private final HttpClientSettings timeouts;

	private final String userAgent;

	private final JsonMapper mapper;

	UpstreamClients(ObjectProvider<RestClient.Builder> builders, JsonMapper mapper,
			@Value("${petclinic.upstream.connect-timeout}") Duration connectTimeout,
			@Value("${petclinic.upstream.read-timeout}") Duration readTimeout,
			@Value("${petclinic.upstream.user-agent}") String userAgent) {
		this.builders = builders;
		this.timeouts = HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout);
		this.userAgent = userAgent;
		// some services leave line breaks unescaped inside their texts, which strict JSON
		// does not allow
		this.mapper = mapper.rebuild().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
	}

	/**
	 * A new client builder with the timeouts and the User-Agent, for a service to add its
	 * own headers to.
	 */
	public RestClient.Builder builder() {
		return builder(timeouts);
	}

	/**
	 * A new client builder for a service that answers slower than the others, with a read
	 * timeout of its own.
	 */
	public RestClient.Builder builder(Duration readTimeout) {
		return builder(timeouts.withReadTimeout(readTimeout));
	}

	private RestClient.Builder builder(HttpClientSettings timeouts) {
		return builders.getObject()
			.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(timeouts))
			.defaultHeader(HttpHeaders.USER_AGENT, userAgent)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
	}

	/**
	 * The mapper that reads the answers of the services.
	 */
	public JsonMapper mapper() {
		return mapper;
	}

	/**
	 * Registers the fields of a service from its operation table.
	 * @param wiring the runtime wiring of the schema
	 * @param operations the operation table on the classpath, such as
	 * <code>cimavet/operations.json</code>
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the URL the paths of the operations start from
	 * @param client the client that calls the service
	 */
	public void register(RuntimeWiring.Builder wiring, String operations, String upstream, String baseUrl,
			RestClient client) {
		RestOperationTable.read(new ClassPathResource(operations), mapper)
			.register(wiring, upstream, baseUrl, client, mapper);
	}

}
