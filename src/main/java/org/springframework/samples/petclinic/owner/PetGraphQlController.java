package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
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
	Pet addPet(@Argument AddPetInput input) {
		return this.petService.addPet(input);
	}

	@MutationMapping
	Pet updatePet(@Argument UpdatePetInput input) {
		return this.petService.updatePet(input);
	}

}
