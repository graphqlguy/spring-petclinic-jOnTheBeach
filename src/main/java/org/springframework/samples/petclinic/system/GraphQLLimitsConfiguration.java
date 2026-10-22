package org.springframework.samples.petclinic.system;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import graphql.language.OperationDefinition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GraphQLLimitsConfiguration {

	@Bean
	MaxQueryDepthInstrumentation maxQueryDepth() {
		return new MaxQueryDepthInstrumentation(5) {

			// Introspection reads the schema, whose nesting is fixed, and GraphQL Java
			// checks it already, so the depth limit applies to the data alone
			@Override
			public InstrumentationContext<ExecutionResult> beginExecuteOperation(
					InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
				if (isIntrospection(parameters.getExecutionContext().getOperationDefinition())) {
					return SimpleInstrumentationContext.noOp();
				}
				return super.beginExecuteOperation(parameters, state);
			}

		};
	}

	@Bean
	MaxQueryComplexityInstrumentation maxQueryComplexity() {
		return new MaxQueryComplexityInstrumentation(200);
	}

	private static boolean isIntrospection(OperationDefinition operation) {
		return operation.getSelectionSet()
			.getSelections()
			.stream()
			.allMatch((selection) -> selection instanceof Field field && field.getName().startsWith("__"));
	}

}
