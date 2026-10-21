package org.springframework.samples.petclinic.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.samples.petclinic.model.Person;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.samples.petclinic.owner.PetService;
import org.springframework.samples.petclinic.vet.VetService;
import org.springframework.stereotype.Controller;

@Controller
class SearchGraphQlController {

	private final OwnerService ownerService;

	private final VetService vetService;

	private final PetService petService;

	SearchGraphQlController(OwnerService ownerService, VetService vetService, PetService petService) {
		this.ownerService = ownerService;
		this.vetService = vetService;
		this.petService = petService;
	}

	@QueryMapping
	List<Object> search(@Argument String text) {
		List<Object> results = new ArrayList<>();
		results.addAll(this.ownerService.search(text));
		results.addAll(this.vetService.search(text));
		results.addAll(this.petService.search(text));
		return results;
	}

	@QueryMapping
	List<Person> people(@Argument String name) {
		List<Person> people = new ArrayList<>();
		people.addAll(this.ownerService.search(name));
		people.addAll(this.vetService.search(name));
		return people;
	}

}
