package org.springframework.samples.petclinic.cimavet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of CIMAVet, the register of veterinary medicines of the Spanish Agency of
 * Medicines and Medical Devices (AEMPS). One fetcher serves them all from an operation
 * table, <code>cimavet/operations.json</code>.
 */
@Configuration(proxyBeanMethods = false)
class CimaVetConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "CIMAVet, the register of veterinary medicines";

	@Bean
	RuntimeWiringConfigurer cimaVetOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.cimavet.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "cimavet/operations.json", UPSTREAM, url, client);
	}

}
