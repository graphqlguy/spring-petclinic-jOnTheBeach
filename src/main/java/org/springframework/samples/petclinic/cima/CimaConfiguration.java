package org.springframework.samples.petclinic.cima;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of CIMA, the register of medicines for people of the Spanish Agency of
 * Medicines and Medical Devices (AEMPS), which a vet reads for the medicines for people
 * they may prescribe when no veterinary medicine fits. One fetcher serves them all from
 * an operation table, <code>cima/operations.json</code>.
 */
@Configuration(proxyBeanMethods = false)
class CimaConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "CIMA, the register of medicines for people";

	@Bean
	RuntimeWiringConfigurer cimaOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.cima.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "cima/operations.json", UPSTREAM, url, client);
	}

}
