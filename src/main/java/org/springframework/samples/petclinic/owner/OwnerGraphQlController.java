package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class OwnerGraphQlController {

	private final OwnerService ownerService;

	OwnerGraphQlController(OwnerService ownerService) {
		this.ownerService = ownerService;
	}

	@QueryMapping
	List<Owner> ownersAll() {
		return this.ownerService.findAll();
	}

	@QueryMapping
	Owner owner(@Argument Integer id) {
		return this.ownerService.findById(id);
	}

	@QueryMapping
	OwnerPage owners(@Argument OwnerFilter filter, @Argument int page, @Argument int size, @Argument OwnerSort sort) {
		return this.ownerService.findOwners(filter, page, size, sort);
	}

}
