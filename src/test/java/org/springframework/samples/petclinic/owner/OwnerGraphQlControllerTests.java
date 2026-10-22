package org.springframework.samples.petclinic.owner;

import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.samples.petclinic.system.GraphQlScalarsConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(OwnerGraphQlController.class)
@Import(GraphQlScalarsConfiguration.class)
class OwnerGraphQlControllerTests {

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
		given(this.ownerService.findById(1)).willReturn(owner);

		this.graphQlTester.document("{ owner(id: 1) { firstName lastName } }")
			.execute()
			.path("owner.lastName")
			.entity(String.class)
			.isEqualTo("Lozano");
	}

}
