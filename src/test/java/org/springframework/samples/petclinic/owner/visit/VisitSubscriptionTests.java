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
