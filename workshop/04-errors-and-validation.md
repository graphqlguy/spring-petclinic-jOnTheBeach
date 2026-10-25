# Chapter 4: Errors and validation

> **User story** · As the receptionist, I want a form to tell me which field is wrong and why, so that I can fix it while the client waits.

When something goes wrong, our API answers `INTERNAL_ERROR` and an id to look up in the log. That keeps internals private, and it leaves the client unable to help its user. In this chapter we turn our exceptions into errors a client can act on, see how GraphQL returns data and errors together, and check arguments before they reach a service.

By the end of this chapter, you will:

- Know where each kind of GraphQL error comes from
- Turn your exceptions into classified errors with `@GraphQlExceptionHandler`
- Return one error per broken field, with the field named
- Read a partial response: data and errors in one answer
- Check arguments with Bean Validation

## 1. Where errors come from

Every error in a GraphQL response sits in the `errors` array, and its `extensions.classification` says where it came from:

| Classification | Comes from | We have seen it |
|---|---|---|
| `ValidationError` | The engine, before any of our code runs: a wrong type, a missing required field, an unknown field | `owners(page: null)` in chapter 1, `"21/10/2026"` in chapter 3 |
| `INTERNAL_ERROR` | An exception from our code that reached the engine untranslated | `vet(id: 99)`, an invalid telephone |
| `NOT_FOUND`, `BAD_REQUEST` | Our code, through an exception handler | This chapter |
| `UNAUTHORIZED`, `FORBIDDEN` | Spring Security | Chapter 5 |

Run the mutation from chapter 3 with a blank last name and a short telephone:

```graphql
mutation {
  addOwner(
    input: { firstName: "Ana", lastName: " ", address: "Calle Larios 3", city: "Málaga", telephone: "123" }
  ) {
    id
  }
}
```

The answer is `INTERNAL_ERROR for 6140054b-...`, and the log has the reason: `Unresolved InvalidInputException`, with `lastName: must not be blank, telephone: Telephone must be a 10-digit number`. The service found both problems and named both fields, and all the client gets is an id.

## 2. Turn exceptions into errors

The starting point's services throw two exceptions of their own:

| Exception | When | Holds |
|---|---|---|
| `EntityNotFoundException` | An unknown id | The entity and the id |
| `InvalidInputException` | Input that breaks a rule of the clinic | One message per field |

**Create a new class** named `GraphQLExceptionHandlers` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLExceptionHandlers.java`

```java
package org.springframework.samples.petclinic.system;

import java.util.List;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
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

}
```

| Element | Meaning |
|---|---|
| `@ControllerAdvice` | A bean whose handlers apply to every controller, as in Spring MVC. |
| `@GraphQlExceptionHandler` | Handles the exception type of the method's first parameter, from any `@QueryMapping`, `@MutationMapping` or `@SchemaMapping` method. |
| `GraphqlErrorBuilder.newError(environment)` | Starts an error with the path and the location of the field that failed. |
| `ErrorType.NOT_FOUND` | The classification. Spring for GraphQL defines `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND` and `INTERNAL_ERROR`. |
| `extensions` | A map for anything else a client needs. Ours names the input field. |
| `List<GraphQLError>` | One failure can produce several errors: here, one per broken field. |

Restart and run the same mutation:

```json
{
  "errors": [
    {
      "message": "must not be blank",
      "path": ["addOwner"],
      "extensions": { "field": "lastName", "classification": "BAD_REQUEST" }
    },
    {
      "message": "Telephone must be a 10-digit number",
      "path": ["addOwner"],
      "extensions": { "field": "telephone", "classification": "BAD_REQUEST" }
    }
  ],
  "data": null
}
```

The frontend's forms read `extensions.field`. Open **Add owner**, leave the last name blank and type a short telephone: each message appears under its field. Then run `vet(id: 99)` from chapter 1 again: it is now a `NOT_FOUND` with the message `Vet with id 99 does not exist`.

## 3. Partial responses

Each field of a query succeeds or fails on its own. Ask for an owner that exists and a vet that does not, in one request:

```graphql
query {
  owner(id: 12) {
    lastName
  }
  vet(id: 99) {
    lastName
  }
}
```

```json
{
  "errors": [
    {
      "message": "Vet with id 99 does not exist",
      "path": ["vet"],
      "extensions": { "classification": "NOT_FOUND" }
    }
  ],
  "data": { "owner": { "lastName": "Navarro" }, "vet": null }
}
```

The client keeps what worked, and the error's `path` says which part failed. How far a failure spreads depends on nullability: `vet` may be `null`, so the failure stops there. In chapter 2, a `null` in the non-null `visits` list emptied the whole `vet`.

## 4. Check arguments with Bean Validation

`owners` accepts any page size. Try `owners(size: 1000000)`: it returns every owner in one page, which is exactly what we deprecated `ownersAll` for. A negative page is worse, `owners(page: -1)` ends as an `INTERNAL_ERROR`.

Spring for GraphQL applies Bean Validation to controller arguments. Add constraints to the `owners` method:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@QueryMapping
OwnerPage owners(@Argument OwnerFilter filter, @Argument @Min(0) int page, @Argument @Min(1) @Max(100) int size,
		@Argument OwnerSort sort) {
	return this.ownerService.findOwners(filter, page, size, sort);
}
```

A broken constraint throws a `ConstraintViolationException`. Add a third handler to `GraphQLExceptionHandlers`, with the imports it needs:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLExceptionHandlers.java`

```java
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

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
```

Restart and try both at once, `owners(page: -1, size: 1000) { totalElements }`:

```json
{
  "errors": [
    {
      "message": "must be less than or equal to 100",
      "path": ["owners"],
      "extensions": { "field": "size", "classification": "BAD_REQUEST" }
    },
    {
      "message": "must be greater than or equal to 0",
      "path": ["owners"],
      "extensions": { "field": "page", "classification": "BAD_REQUEST" }
    }
  ],
  "data": null
}
```

Three layers now check what a client sends: the schema's types, which the engine enforces; constraints on arguments; and the rules in the services. The handlers give all of them the same shape. Chapter 9 adds one more limit, on the shape of the query itself.

## Exercise: a range that ends before it starts

`visits(from: "2026-10-31", to: "2026-10-21")` returns an empty list today, which looks like an empty calendar. Make it a `BAD_REQUEST` on the `to` field instead, with a message that says what is wrong. Throw an `InvalidInputException` from `VisitGraphQLController`: the handler from step 2 does the rest.

<details>
<summary>Show solution</summary>

`src/main/java/org/springframework/samples/petclinic/owner/VisitGraphQLController.java`

```java
import org.springframework.samples.petclinic.system.InvalidInputException;

@QueryMapping
List<Visit> visits(@Argument LocalDate from, @Argument LocalDate to) {
	if (to.isBefore(from)) {
		throw new InvalidInputException("to", "The end date " + to + " comes before the start date " + from);
	}
	return this.visitService.findVisits(from, to);
}
```

```json
{
  "errors": [
    {
      "message": "The end date 2026-10-21 comes before the start date 2026-10-31",
      "path": ["visits"],
      "extensions": { "field": "to", "classification": "BAD_REQUEST" }
    }
  ],
  "data": null
}
```

</details>

## Recap

- The engine reports a malformed request as a `ValidationError`, before our code runs.
- An untranslated exception reaches the client as `INTERNAL_ERROR`, with the details in the log.
- `@GraphQlExceptionHandler` turns our exceptions into classified errors, one or several, with whatever `extensions` a client needs.
- A response can carry data and errors together; nullability decides how far a failure spreads.
- Bean Validation checks arguments before the service runs.

Chapter 5 decides who may change the clinic's data.
