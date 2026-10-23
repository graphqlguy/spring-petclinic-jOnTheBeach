package org.springframework.samples.petclinic.ine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the Spanish National Statistics Institute (INE), such as the population
 * of every municipality. One fetcher serves them all from an operation table,
 * <code>ine/operations.json</code>, which a generator wrote from the documentation pages
 * of the API.
 */
@Configuration(proxyBeanMethods = false)
class IneConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The Spanish National Statistics Institute (INE)";

	@Bean
	RuntimeWiringConfigurer ineOperations(UpstreamClients clients, @Value("${petclinic.upstream.ine.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "ine/operations.json", UPSTREAM, url, client);
	}

}
