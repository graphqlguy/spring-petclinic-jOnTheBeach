package org.springframework.samples.petclinic.junta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the open data API of the Junta de Andalucía: public holidays, the tourism
 * register, regulations, museums, libraries and more. One fetcher serves them all from an
 * operation table, <code>junta/operations.json</code>, which a generator wrote from the
 * OpenAPI documents of fourteen datasets.
 */
@Configuration(proxyBeanMethods = false)
class JuntaConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The open data API of the Junta de Andalucía";

	@Bean
	RuntimeWiringConfigurer juntaOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.junta.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "junta/operations.json", UPSTREAM, url, client);
	}

}
