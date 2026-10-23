package org.springframework.samples.petclinic.mosquitoalert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of Mosquito Alert, the citizen science project that maps mosquitoes in
 * Spain. One fetcher serves them all from an operation table,
 * <code>mosquitoalert/operations.json</code>, which a generator wrote from the OpenAPI
 * document of the service.
 */
@Configuration(proxyBeanMethods = false)
class MosquitoAlertConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "Mosquito Alert";

	@Bean
	RuntimeWiringConfigurer mosquitoAlertOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.mosquitoalert.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "mosquitoalert/operations.json", UPSTREAM, url, client);
	}

}
