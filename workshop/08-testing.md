# Chapter 8: Testing

> **User story** · As the clinic manager, I want every feature guarded by tests that use the API the way clients do, so that tomorrow's changes keep today's promises.

The starting point's services have tests already. In this chapter we test the GraphQL layer itself: the queries, the errors, the security rules and the subscription, through the same requests a client sends. Spring for GraphQL's `GraphQlTester` does the work.

By the end of this chapter, you will:

- Know which kind of test to write for which part of a GraphQL API
- Write integration tests with `HttpGraphQlTester`, with and without a token
- Assert on errors and on partial responses
- Test a subscription
- Test one controller fast with the `@GraphQlTest` slice

## 1. The test pyramid for GraphQL

| Kind | Starts | Checks | Speed |
|---|---|---|---|
| Service tests | Spring and the database | The business rules (`OwnerServiceTests` and the others in the starting point) | Fast |
| `@GraphQlTest` slice | The GraphQL layer, with mocked services | One controller and the schema it serves | Fastest |
| `HttpGraphQlTester` over HTTP | The whole application on a random port | Everything a client sees: transport, security, errors | Slowest |

Most tests belong at the bottom. The few HTTP tests prove that the parts work together.

## 2. The dependencies

Add two test dependencies next to the other test dependencies:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-graphql-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.projectreactor</groupId>
  <artifactId>reactor-test</artifactId>
  <scope>test</scope>
</dependency>
```

The first brings `GraphQlTester` and its Spring Boot support. The second brings `StepVerifier`, for the subscription test in step 5.

> [!NOTE]
> With Gradle, add `testImplementation 'org.springframework.boot:spring-boot-starter-graphql-test'` and `testImplementation 'io.projectreactor:reactor-test'` to `build.gradle`.

## 3. Integration tests: HttpGraphQlTester

**Create a new test class** named `PetclinicGraphQLTests` in a new `org.springframework.samples.petclinic.graphql` package, under `src/test/java`:

`src/test/java/org/springframework/samples/petclinic/graphql/PetclinicGraphQLTests.java`

```java
package org.springframework.samples.petclinic.graphql;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class PetclinicGraphQLTests {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Test
	void findsTheOwnersOfACity() {
		graphQlTester.document("""
				query {
				  owners(filter: { city: "Torremolinos" }) {
				    totalElements
				    content {
				      lastName
				    }
				  }
				}""")
			.execute()
			.path("owners.totalElements")
			.entity(Integer.class)
			.isEqualTo(3)
			.path("owners.content[*].lastName")
			.entityList(String.class)
			.containsExactly("Beach", "Morales", "Ramos");
	}

}
```

| Element | Meaning |
|---|---|
| `RANDOM_PORT` | Starts the whole application on a free port, as `./mvnw spring-boot:run` would. |
| `@AutoConfigureHttpGraphQlTester` | Provides an `HttpGraphQlTester` that sends requests to that port. |
| `document(...)` | The operation to send, here as a Java text block. |
| `execute()` | Sends it. The first assertion fails the test if the response has an error the test does not expect. |
| `path("owners.totalElements")` | Picks a value from `data`, with the dots and `[*]` of JSONPath. |
| `entity(...)`, `entityList(...)` | Converts the value to Java, then asserts on it. |

Run it from your IDE, or with `./mvnw test -Dtest=PetclinicGraphQLTests`. To see a failure, change the expected `3` to `4` and run it again:

```text
owners.totalElements expected:<4> but was:<3>
Request: document='query {
  owners(filter: { city: "Torremolinos" }) {
...
```

The message names the path and both values, then prints the request that produced them. Change it back to `3`.

## 4. Errors, and mutations with a token

An unexpected error fails a test at its first assertion. A test that expects an error says which, with `errors()`. Add this test to the class:

```java
@Test
void reportsAnUnknownVet() {
	graphQlTester.document("{ vet(id: 99) { lastName } }")
		.execute()
		.errors()
		.expect((error) -> error.getErrorType() == ErrorType.NOT_FOUND)
		.verify()
		.path("vet")
		.valueIsNull();
}
```

`expect(...)` names the errors the test expects, and `verify()` fails the test if any other error arrived. The partial response from chapter 4 is checked too: `vet` is `null`.

Mutations need a token. A helper signs in through the `login` mutation, and `mutate()` makes a copy of the tester that sends the token with every request. Add the helper and a test:

```java
@Test
void staffCanAddAnOwner() {
	signedIn("staff").document("""
			mutation {
			  addOwner(input: { firstName: "Ana", lastName: "Lozano", address: "Calle Larios 3",
			                    city: "Nerja", telephone: "9521234567" }) {
			    id
			    lastName
			  }
			}""")
		.execute()
		.path("addOwner.lastName")
		.entity(String.class)
		.isEqualTo("Lozano");
}

private HttpGraphQlTester signedIn(String username) {
	String token = graphQlTester.document("mutation ($user: String!) { login(username: $user, password: $user) }")
		.variable("user", username)
		.execute()
		.path("login")
		.entity(String.class)
		.get();
	return graphQlTester.mutate().header("Authorization", "Bearer " + token).build();
}
```

Our two users have their name as their password, which keeps the helper short.

## 5. Test a subscription

A subscription test needs to listen first and book second. Over HTTP that needs a WebSocket client, so this test runs the operation in-process instead, through the `GraphQlTester` that `@AutoConfigureGraphQlTester` provides. It calls the same engine, without the network. **Create a new test class** next to `VisitService`'s tests:

`src/test/java/org/springframework/samples/petclinic/owner/visit/VisitSubscriptionTests.java`

```java
package org.springframework.samples.petclinic.owner.visit;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;

@SpringBootTest
@AutoConfigureGraphQlTester
class VisitSubscriptionTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@Autowired
	private VisitService visitService;

	@Test
	void announcesEachBookedVisit() {
		Flux<String> descriptions = graphQlTester.document("subscription { visitBooked { description } }")
			.executeSubscription()
			.toFlux("visitBooked.description", String.class);

		StepVerifier.create(descriptions)
			.then(() -> visitService.addVisit(new AddVisitInput(7, 1, null, "check-up")))
			.expectNext("check-up")
			.thenCancel()
			.verify(Duration.ofSeconds(5));
	}

}
```

| Element | Meaning |
|---|---|
| `executeSubscription().toFlux(...)` | Subscribes, and turns each event into the value at the path. |
| `StepVerifier.create(...)` | Subscribes to the stream and checks its events one by one. |
| `then(...)` | Runs once the subscription is active: here, it books a visit through the service. |
| `expectNext("check-up")` | The next event must be the visit just booked. |
| `thenCancel()` | Ends the subscription, which would otherwise wait for more visits. |

## 6. Faster feedback: the @GraphQlTest slice

A slice starts only the GraphQL layer: the schema, the controller under test and its exception handlers, without the web server or the database. The service is a Mockito mock, so the test checks the controller and the schema alone. The schema uses our `Date` scalar, so the slice needs `GraphQLScalarsConfiguration`. That class is package-private, so first make it `public`:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLScalarsConfiguration.java`

```java
public class GraphQLScalarsConfiguration {
```

**Create a new test class** next to the controller:

`src/test/java/org/springframework/samples/petclinic/owner/OwnerGraphQLControllerTests.java`

```java
package org.springframework.samples.petclinic.owner;

import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.samples.petclinic.system.GraphQLScalarsConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(OwnerGraphQLController.class)
@Import(GraphQLScalarsConfiguration.class)
class OwnerGraphQLControllerTests {

	@Autowired
	private GraphQlTester graphQlTester;

	@MockitoBean
	private OwnerService ownerService;

	@Test
	void returnsTheOwnerTheServiceFinds() {
		Owner owner = new Owner();
		owner.setId(1);
		owner.setFirstName("Ana");
		owner.setLastName("Lozano");
		given(ownerService.findById(1)).willReturn(owner);

		graphQlTester.document("{ owner(id: 1) { firstName lastName } }")
			.execute()
			.path("owner.lastName")
			.entity(String.class)
			.isEqualTo("Lozano");
	}

}
```

The test runs in about a third of a second, against several seconds for a test that starts the whole application.

## 7. Run all the tests

```bash
./mvnw test
```

Petclinic's tests, the starting point's service tests and the new ones run together:

```text
Tests run: 114, Failures: 0, Errors: 0, Skipped: 0
```

## Exercise: prove the authorization rules

Tests of the happy path leave the security of chapter 5 unguarded: a careless change could open a mutation to everyone, and every test above would still pass. Add two tests to `PetclinicGraphQLTests`:

1. `guest` tries to add an owner, and gets `FORBIDDEN`.
2. An anonymous caller asks for owner 6's last name and telephone, gets the last name, a `null` telephone, and an `UNAUTHORIZED` error.

<details>
<summary>Show solution</summary>

`src/test/java/org/springframework/samples/petclinic/graphql/PetclinicGraphQLTests.java`

```java
@Test
void guestCannotAddAnOwner() {
	signedIn("guest").document("""
			mutation {
			  addOwner(input: { firstName: "Ana", lastName: "Lozano", address: "Calle Larios 3",
			                    city: "Nerja", telephone: "9521234567" }) {
			    id
			  }
			}""")
		.execute()
		.errors()
		.expect((error) -> error.getErrorType() == ErrorType.FORBIDDEN)
		.verify();
}

@Test
void hidesTheTelephoneFromAnonymousCallers() {
	graphQlTester.document("{ owner(id: 12) { lastName telephone } }")
		.execute()
		.errors()
		.expect((error) -> error.getErrorType() == ErrorType.UNAUTHORIZED)
		.verify()
		.path("owner.lastName")
		.entity(String.class)
		.isEqualTo("Navarro")
		.path("owner.telephone")
		.valueIsNull();
}
```

With both, `./mvnw test` runs 116 tests.

</details>

## Recap

- `HttpGraphQlTester` tests the API over HTTP, the way a client uses it.
- `execute()` fails on any error; `errors().expect(...).verify()` states the errors a test expects.
- `mutate()` copies a tester with a header, such as a token.
- An in-process `GraphQlTester` and `StepVerifier` test a subscription.
- `@GraphQlTest` tests one controller against mocked services, in a fraction of a second.

Chapter 9 gets the schema ready for its next kind of client: AI agents.
