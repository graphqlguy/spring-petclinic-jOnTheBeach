package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class VisitGraphQlController {

	private final VisitService visitService;

	VisitGraphQlController(VisitService visitService) {
		this.visitService = visitService;
	}

	@QueryMapping
	List<Visit> visits(@Argument LocalDate from, @Argument LocalDate to) {
		return this.visitService.findVisits(from, to);
	}

	@MutationMapping
	Visit addVisit(@Argument AddVisitInput input) {
		return this.visitService.addVisit(input);
	}

}
