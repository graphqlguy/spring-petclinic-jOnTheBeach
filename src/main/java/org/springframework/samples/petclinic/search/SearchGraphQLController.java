package org.springframework.samples.petclinic.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.samples.petclinic.model.Person;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.samples.petclinic.owner.pet.PetService;
import org.springframework.samples.petclinic.vet.VetService;
import org.springframework.stereotype.Controller;

@Controller
class SearchGraphQLController {

	private final OwnerService ownerService;

	private final VetService vetService;

	private final PetService petService;

	SearchGraphQLController(OwnerService ownerService, VetService vetService, PetService petService) {
		this.ownerService = ownerService;
		this.vetService = vetService;
		this.petService = petService;
	}

	@QueryMapping
	List<Object> search(@Argument String text) {
		List<Object> results = new ArrayList<>();
		results.addAll(ownerService.search(text));
		results.addAll(vetService.search(text));
		results.addAll(petService.search(text));
		return results;
	}

	@QueryMapping
	List<Person> people(@Argument String name) {
		List<Person> people = new ArrayList<>();
		people.addAll(ownerService.search(name));
		people.addAll(vetService.search(name));
		return people;
	}

}
