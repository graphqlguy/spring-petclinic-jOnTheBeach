package org.springframework.samples.petclinic.owner.pet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class PetGraphQLController {

	private final PetService petService;

	PetGraphQLController(PetService petService) {
		this.petService = petService;
	}

	@QueryMapping
	List<PetType> petTypes() {
		return petService.findPetTypes();
	}

	@MutationMapping
	Pet addPet(@Argument AddPetInput input) {
		return petService.addPet(input);
	}

	@MutationMapping
	Pet updatePet(@Argument UpdatePetInput input) {
		return petService.updatePet(input);
	}

}
