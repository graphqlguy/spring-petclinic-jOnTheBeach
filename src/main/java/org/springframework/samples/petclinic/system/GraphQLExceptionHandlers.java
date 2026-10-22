package org.springframework.samples.petclinic.system;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
class GraphQLExceptionHandlers {

	@GraphQlExceptionHandler
	GraphQLError notFound(EntityNotFoundException ex, DataFetchingEnvironment environment) {
		return GraphqlErrorBuilder.newError(environment)
			.errorType(ErrorType.NOT_FOUND)
			.message(ex.getMessage())
			.build();
	}

	@GraphQlExceptionHandler
	List<GraphQLError> invalidInput(InvalidInputException ex, DataFetchingEnvironment environment) {
		return ex.getFieldErrors()
			.entrySet()
			.stream()
			.map((error) -> GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.BAD_REQUEST)
				.message(error.getValue())
				.extensions(Map.of("field", error.getKey()))
				.build())
			.toList();
	}

	@GraphQlExceptionHandler
	List<GraphQLError> notAnId(BindException ex, DataFetchingEnvironment environment) {
		return ex.getFieldErrors()
			.stream()
			.map((error) -> GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.BAD_REQUEST)
				.message("'" + error.getRejectedValue()
						+ "' is not an id. An id is a number, which queries such as search, owners and vets return.")
				.build())
			.toList();
	}

}
