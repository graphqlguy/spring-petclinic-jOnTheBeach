package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
	OwnerPage owners(@Argument OwnerFilter filter, @Argument @Min(0) int page, @Argument @Min(1) @Max(100) int size,
			@Argument OwnerSort sort) {
		return this.ownerService.findOwners(filter, page, size, sort);
	}

	@MutationMapping
	Owner addOwner(@Argument AddOwnerInput input) {
		return this.ownerService.addOwner(input);
	}

	@MutationMapping
	Owner updateOwner(@Argument UpdateOwnerInput input) {
		return this.ownerService.updateOwner(input);
	}

}
