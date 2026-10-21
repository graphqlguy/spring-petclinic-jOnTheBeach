package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
class PetGraphQlController {

	private final PetService petService;

	PetGraphQlController(PetService petService) {
		this.petService = petService;
	}

	@QueryMapping
	List<PetType> petTypes() {
		return this.petService.findPetTypes();
	}

	@MutationMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Pet addPet(@Argument AddPetInput input) {
		return this.petService.addPet(input);
	}

	@MutationMapping
	@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
	Pet updatePet(@Argument UpdatePetInput input) {
		return this.petService.updatePet(input);
	}

}
