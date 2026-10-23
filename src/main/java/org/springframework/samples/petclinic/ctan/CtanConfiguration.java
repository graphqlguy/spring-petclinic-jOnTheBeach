package org.springframework.samples.petclinic.ctan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of the Network of Transport Consortia of Andalusia (CTAN): the lines, stops,
 * timetables and fares of the public transport between towns. One fetcher serves them all
 * from an operation table, <code>ctan/operations.json</code>.
 */
@Configuration(proxyBeanMethods = false)
class CtanConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "The transport consortia of Andalusia (CTAN)";

	@Bean
	RuntimeWiringConfigurer ctanOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.ctan.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "ctan/operations.json", UPSTREAM, url, client);
	}

}
