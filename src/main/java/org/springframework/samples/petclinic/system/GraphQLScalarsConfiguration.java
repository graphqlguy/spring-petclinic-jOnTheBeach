package org.springframework.samples.petclinic.system;

import graphql.scalars.ExtendedScalars;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration(proxyBeanMethods = false)
public class GraphQLScalarsConfiguration {

	@Bean
	RuntimeWiringConfigurer scalars() {
		return (wiring) -> wiring.scalar(ExtendedScalars.Date);
	}

}
