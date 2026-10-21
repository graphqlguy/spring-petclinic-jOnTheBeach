package org.springframework.samples.petclinic.vet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitService;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQlController {

	private final VetService vetService;

	private final VisitService visitService;

	VetGraphQlController(VetService vetService, VisitService visitService) {
		this.vetService = vetService;
		this.visitService = visitService;
	}

	@QueryMapping
	List<Vet> vets() {
		return this.vetService.findAll();
	}

	@QueryMapping
	Vet vet(@Argument Integer id) {
		return this.vetService.findById(id);
	}

	@BatchMapping
	Map<Vet, List<Visit>> visits(List<Vet> vets) {
		List<Integer> vetIds = vets.stream().map(Vet::getId).toList();
		Map<Integer, List<Visit>> visitsByVetId = this.visitService.findVisitsByVetIds(vetIds);
		return vets.stream()
			.collect(Collectors.toMap((vet) -> vet, (vet) -> visitsByVetId.getOrDefault(vet.getId(), List.of())));
	}

	@BatchMapping(field = "vets")
	Map<Specialty, List<Vet>> specialtyVets(List<Specialty> specialties) {
		List<Integer> specialtyIds = specialties.stream().map(Specialty::getId).toList();
		Map<Integer, List<Vet>> vetsBySpecialtyId = this.vetService.findVetsBySpecialtyIds(specialtyIds);
		return specialties.stream()
			.collect(Collectors.toMap((specialty) -> specialty,
					(specialty) -> vetsBySpecialtyId.getOrDefault(specialty.getId(), List.of())));
	}

}
