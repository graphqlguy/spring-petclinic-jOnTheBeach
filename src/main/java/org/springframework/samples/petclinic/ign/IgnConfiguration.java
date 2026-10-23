package org.springframework.samples.petclinic.ign;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the OGC API Features service of the National Geographic Institute:
 * administrative units, geographic names, population centres and geodetic networks. One
 * fetcher serves them all from an operation table, <code>ign/operations.json</code>,
 * which a generator wrote from the OpenAPI document of the service.
 */
@Configuration(proxyBeanMethods = false)
class IgnConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The API-Features service of the IGN";

	@Bean
	RuntimeWiringConfigurer ignOperations(UpstreamClients clients, @Value("${petclinic.upstream.ign.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "ign/operations.json", UPSTREAM, url, client);
	}

}
