package org.springframework.samples.petclinic.system;

import graphql.validation.rules.ValidationRules;
import graphql.validation.schemawiring.ValidationSchemaWiring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration(proxyBeanMethods = false)
class GraphQLValidationConfiguration {

	@Bean
	RuntimeWiringConfigurer validation() {
		ValidationRules rules = ValidationRules.newValidationRules().build();
		return (wiring) -> wiring.directiveWiring(new ValidationSchemaWiring(rules));
	}

}
