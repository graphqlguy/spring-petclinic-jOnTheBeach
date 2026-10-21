package org.springframework.samples.petclinic.owner.visit;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class VisitGraphQLController {

	private final VisitService visitService;

	VisitGraphQLController(VisitService visitService) {
		this.visitService = visitService;
	}

	@MutationMapping
	Visit addVisit(@Argument AddVisitInput input) {
		return visitService.addVisit(input);
	}

}
