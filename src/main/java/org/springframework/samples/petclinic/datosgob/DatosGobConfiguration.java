package org.springframework.samples.petclinic.datosgob;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.samples.petclinic.upstream.UpstreamClients;
import org.springframework.web.client.RestClient;

/**
 * The fields of datos.gob.es, the national catalogue of the open data of Spain, which
 * lists the datasets of every public body. One fetcher serves them all from an operation
 * table, <code>datosgob/operations.json</code>.
 */
@Configuration(proxyBeanMethods = false)
class DatosGobConfiguration {

	/**
	 * The name of the service in errors, in words a client can read.
	 */
	static final String UPSTREAM = "datos.gob.es, the national catalogue of open data";

	@Bean
	RuntimeWiringConfigurer datosGobOperations(UpstreamClients clients,
			@Value("${petclinic.upstream.datosgob.url}") String url) {
		RestClient client = clients.builder().build();
		return (wiring) -> clients.register(wiring, "datosgob/operations.json", UPSTREAM, url, client);
	}

}
