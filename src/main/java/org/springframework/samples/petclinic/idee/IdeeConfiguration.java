package org.springframework.samples.petclinic.idee;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the OGC API Features service of the National Cartographic System of
 * Spain: addresses, land use and cover, hydrography and transport networks. One fetcher
 * serves them all from an operation table, <code>idee/operations.json</code>, which a
 * generator wrote from the OpenAPI document of the service.
 */
@Configuration(proxyBeanMethods = false)
class IdeeConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The API-Features service of the National Cartographic System";

	@Bean
	RuntimeWiringConfigurer ideeOperations(UpstreamClients clients, @Value("${petclinic.upstream.idee.url}") String url,
			@Value("${petclinic.upstream.idee.read-timeout}") Duration readTimeout) {
		RestClient client = clients.builder(readTimeout).build();
		return (wiring) -> clients.register(wiring, "idee/operations.json", UPSTREAM, url, client);
	}

}
