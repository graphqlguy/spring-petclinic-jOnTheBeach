package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class OwnerGraphQLController {

	private final OwnerService ownerService;

	OwnerGraphQLController(OwnerService ownerService) {
		this.ownerService = ownerService;
	}

	@QueryMapping
	List<Owner> ownersAll() {
		return ownerService.findAll();
	}

	@QueryMapping
	Owner owner(@Argument Integer id) {
		return ownerService.findById(id);
	}

	@QueryMapping
	OwnerPage owners(@Argument OwnerFilter filter, @Argument int page, @Argument int size, @Argument OwnerSort sort) {
		return ownerService.findOwners(filter, page, size, sort);
	}

	@MutationMapping
	Owner addOwner(@Argument AddOwnerInput input) {
		return ownerService.addOwner(input);
	}

	@MutationMapping
	Owner updateOwner(@Argument UpdateOwnerInput input) {
		return ownerService.updateOwner(input);
	}

}
