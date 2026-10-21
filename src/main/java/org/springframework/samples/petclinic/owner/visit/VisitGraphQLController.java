package org.springframework.samples.petclinic.owner.visit;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.event.TransactionalEventListener;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Controller
class VisitGraphQLController {

	private final VisitService visitService;

	private final Sinks.Many<Visit> bookedVisits = Sinks.many().multicast().directBestEffort();

	VisitGraphQLController(VisitService visitService) {
		this.visitService = visitService;
	}

	@MutationMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Visit addVisit(@Argument AddVisitInput input) {
		return visitService.addVisit(input);
	}

	@TransactionalEventListener
	void onVisitBooked(VisitBookedEvent event) {
		bookedVisits.tryEmitNext(visitService.findWithDetails(event.visitId()));
	}

	@SubscriptionMapping
	Flux<Visit> visitBooked() {
		return bookedVisits.asFlux();
	}

}
