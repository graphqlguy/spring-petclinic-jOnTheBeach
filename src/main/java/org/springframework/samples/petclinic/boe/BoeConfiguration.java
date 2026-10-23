package org.springframework.samples.petclinic.boe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the open data API of the Boletín Oficial del Estado (BOE), Spain's
 * official gazette: the consolidated laws, such as the law on the welfare of animals, and
 * the summaries of each day. One fetcher serves them all from an operation table,
 * <code>boe/operations.json</code>, which a generator wrote from the documentation pages
 * of the API.
 */
@Configuration(proxyBeanMethods = false)
class BoeConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The open data API of the Boletín Oficial del Estado (BOE)";

	@Bean
	RuntimeWiringConfigurer boeOperations(UpstreamClients clients, @Value("${petclinic.upstream.boe.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "boe/operations.json", UPSTREAM, url, client);
	}

}
