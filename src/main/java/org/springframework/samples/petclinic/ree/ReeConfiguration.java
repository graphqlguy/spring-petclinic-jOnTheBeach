package org.springframework.samples.petclinic.ree;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of REData, the open data of Red Eléctrica, the operator of the Spanish
 * electricity grid: demand, generation, exchanges, the grid and the markets. One fetcher
 * serves all 68 indicators from an operation table, <code>ree/operations.json</code>.
 */
@Configuration(proxyBeanMethods = false)
class ReeConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "REData, the open data of Red Eléctrica";

	@Bean
	RuntimeWiringConfigurer reeOperations(UpstreamClients clients, @Value("${petclinic.upstream.ree.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "ree/operations.json", UPSTREAM, url, client);
	}

}
