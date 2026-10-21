package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.event.TransactionalEventListener;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Controller
class VisitGraphQlController {

	private final VisitService visitService;

	private final Sinks.Many<Visit> bookedVisits = Sinks.many().multicast().directBestEffort();

	VisitGraphQlController(VisitService visitService) {
		this.visitService = visitService;
	}

	@QueryMapping
	List<Visit> visits(@Argument LocalDate from, @Argument LocalDate to) {
		if (to.isBefore(from)) {
			throw new InvalidInputException("to", "The end date " + to + " comes before the start date " + from);
		}
		return this.visitService.findVisits(from, to);
	}

	@MutationMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Visit addVisit(@Argument AddVisitInput input) {
		return this.visitService.addVisit(input);
	}

	@TransactionalEventListener
	void onVisitBooked(VisitBookedEvent event) {
		this.bookedVisits.tryEmitNext(this.visitService.findWithDetails(event.visitId()));
	}

	@SubscriptionMapping
	Flux<Visit> visitBooked(@Argument Integer vetId) {
		return this.bookedVisits.asFlux()
			.filter((visit) -> vetId == null || (visit.getVet() != null && vetId.equals(visit.getVet().getId())));
	}

}
