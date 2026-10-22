package org.springframework.samples.petclinic.vet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.samples.petclinic.owner.visit.Visit;
import org.springframework.samples.petclinic.owner.visit.VisitService;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQLController {

	private final VetService vetService;

	private final VisitService visitService;

	VetGraphQLController(VetService vetService, VisitService visitService) {
		this.vetService = vetService;
		this.visitService = visitService;
	}

	@QueryMapping
	List<Vet> vets() {
		return vetService.findAll();
	}

	@QueryMapping
	Vet vet(@Argument Integer id) {
		return vetService.findById(id);
	}

	@BatchMapping
	Map<Vet, List<Visit>> visits(List<Vet> vets) {
		List<Integer> vetIds = vets.stream().map(Vet::getId).toList();
		Map<Integer, List<Visit>> visitsByVetId = visitService.findVisitsByVetIds(vetIds);
		return vets.stream()
			.collect(Collectors.toMap((vet) -> vet, (vet) -> visitsByVetId.getOrDefault(vet.getId(), List.of())));
	}

	@BatchMapping
	Map<Vet, Integer> visitCount(List<Vet> vets) {
		Map<Vet, List<Visit>> visits = visits(vets);
		return vets.stream().collect(Collectors.toMap((vet) -> vet, (vet) -> visits.get(vet).size()));
	}

}
