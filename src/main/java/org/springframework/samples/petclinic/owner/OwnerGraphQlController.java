package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
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
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Owner addOwner(@Argument AddOwnerInput input) {
		return this.ownerService.addOwner(input);
	}

	@MutationMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Owner updateOwner(@Argument UpdateOwnerInput input) {
		return this.ownerService.updateOwner(input);
	}

	@SchemaMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	String telephone(Owner owner) {
		return owner.getTelephone();
	}

}
