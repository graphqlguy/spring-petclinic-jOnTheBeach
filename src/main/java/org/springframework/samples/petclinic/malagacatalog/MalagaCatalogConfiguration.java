package org.springframework.samples.petclinic.malagacatalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the catalogue of the open data portal of the city of Málaga, which runs
 * CKAN: its datasets, resources and the tables of its datastore. One fetcher serves them
 * all from an operation table, <code>malagacatalog/operations.json</code>, which a
 * generator wrote from the documentation of the CKAN Action API.
 */
@Configuration(proxyBeanMethods = false)
class MalagaCatalogConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The open data portal of the city of Málaga";

	@Bean
	RuntimeWiringConfigurer malagaCatalogOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.malagacatalog.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "malagacatalog/operations.json", UPSTREAM, url, client);
	}

}
