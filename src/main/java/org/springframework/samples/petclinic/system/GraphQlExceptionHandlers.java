package org.springframework.samples.petclinic.system;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

@ControllerAdvice
class GraphQlExceptionHandlers {

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
	List<GraphQLError> constraintViolation(ConstraintViolationException ex, DataFetchingEnvironment environment) {
		return ex.getConstraintViolations()
			.stream()
			.map((violation) -> GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.BAD_REQUEST)
				.message(violation.getMessage())
				.extensions(Map.of("field", argumentName(violation)))
				.build())
			.toList();
	}

	// The path of a violation on a method argument ends with the argument's name
	private static String argumentName(ConstraintViolation<?> violation) {
		String name = "";
		for (Path.Node node : violation.getPropertyPath()) {
			name = node.getName();
		}
		return name;
	}

}
