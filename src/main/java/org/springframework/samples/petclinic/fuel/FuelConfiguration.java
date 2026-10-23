package org.springframework.samples.petclinic.fuel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the fuel prices of the Ministry for the Ecological Transition, which
 * every filling station in Spain reports. One fetcher serves them all from an operation
 * table, <code>fuel/operations.json</code>, which a generator wrote from the help page of
 * the service.
 */
@Configuration(proxyBeanMethods = false)
class FuelConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The fuel prices of the Ministry for the Ecological Transition";

	@Bean
	RuntimeWiringConfigurer fuelOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.fuel.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "fuel/operations.json", UPSTREAM, url, client);
	}

}
