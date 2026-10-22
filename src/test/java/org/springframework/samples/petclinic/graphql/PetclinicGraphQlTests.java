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
class PetclinicGraphQlTests {

	@Autowired
	private HttpGraphQlTester graphQlTester;

	@Test
	void findsTheOwnersOfACity() {
		this.graphQlTester.document("""
				query {
				  owners(filter: { city: "Marbella" }) {
				    totalElements
				    content {
				      lastName
				    }
				  }
				}""")
			.execute()
			.path("owners.totalElements")
			.entity(Integer.class)
			.isEqualTo(2)
			.path("owners.content[*].lastName")
			.entityList(String.class)
			.containsExactly("Fernández", "Morales");
	}

	@Test
	void reportsAnUnknownVet() {
		this.graphQlTester.document("{ vet(id: 99) { lastName } }")
			.execute()
			.errors()
			.expect((error) -> error.getErrorType() == ErrorType.NOT_FOUND)
			.verify()
			.path("vet")
			.valueIsNull();
	}

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
		this.graphQlTester.document("{ owner(id: 6) { lastName telephone } }")
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

	private HttpGraphQlTester signedIn(String username) {
		String token = this.graphQlTester.document("mutation ($user: String!) { login(username: $user, password: $user) }")
			.variable("user", username)
			.execute()
			.path("login")
			.entity(String.class)
			.get();
		return this.graphQlTester.mutate().header("Authorization", "Bearer " + token).build();
	}

}
