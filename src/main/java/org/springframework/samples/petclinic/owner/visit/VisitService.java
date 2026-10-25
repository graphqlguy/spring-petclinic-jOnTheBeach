/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner.visit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.samples.petclinic.owner.pet.Pet;
import org.springframework.samples.petclinic.owner.pet.PetRepository;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.system.LatencySimulator;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reads and books visits. Booking a visit publishes a {@link VisitBookedEvent}.
 */
@Service
@Transactional(readOnly = true)
public class VisitService {

	private static final Logger logger = LoggerFactory.getLogger(VisitService.class);

	private final VisitRepository visits;

	private final PetRepository pets;

	private final VetRepository vets;

	private final ApplicationEventPublisher events;

	private final LatencySimulator latency;

	VisitService(VisitRepository visits, PetRepository pets, VetRepository vets, ApplicationEventPublisher events,
			LatencySimulator latency) {
		this.visits = visits;
		this.pets = pets;
		this.vets = vets;
		this.events = events;
		this.latency = latency;
	}

	/**
	 * The visits of one vet, newest first.
	 * <p>
	 * One SQL statement for each vet: fine for one vet, and deliberately naive for a list
	 * of them. Chapter 2 replaces it with {@link #findVisitsByVetIds(List)}.
	 */
	public List<Visit> findByVet(Vet vet) {
		latency.pause();
		return visits.findByVetIdOrderByDateDescIdDesc(vet.getId());
	}

	/**
	 * The visits of many vets in one SQL statement, newest first, keyed by vet id. A vet
	 * without visits is missing from the map.
	 */
	public Map<Integer, List<Visit>> findVisitsByVetIds(List<Integer> vetIds) {
		logger.info("Batch fetching visits for {} vets", vetIds.size());
		latency.pause();
		Map<Integer, List<Visit>> visitsByVetId = new HashMap<>();
		for (Visit visit : visits.findByVetIds(vetIds)) {
			visitsByVetId.computeIfAbsent(visit.getVet().getId(), (id) -> new ArrayList<>()).add(visit);
		}
		return visitsByVetId;
	}

	/**
	 * The visits from one date to another, both included, in date order.
	 */
	public List<Visit> findVisits(LocalDate from, LocalDate to) {
		latency.pause();
		return visits.findByDateBetweenOrderByDateAsc(from, to);
	}

	/**
	 * A visit with its pet, owner, pet type and vet already read, for code that runs
	 * after the transaction, such as a subscription.
	 * @throws EntityNotFoundException when no visit has the id
	 */
	public Visit findWithDetails(Integer id) {
		return visits.findWithDetailsById(id).orElseThrow(() -> new EntityNotFoundException("Visit", id));
	}

	/**
	 * Books a visit for a pet, and publishes a {@link VisitBookedEvent}.
	 * @throws EntityNotFoundException when the pet or the vet does not exist
	 * @throws InvalidInputException when the description is missing or too long
	 */
	@Transactional
	public Visit addVisit(AddVisitInput input) {
		latency.pause();
		Pet pet = pets.findById(input.petId()).orElseThrow(() -> new EntityNotFoundException("Pet", input.petId()));
		Vet vet = (input.vetId() != null)
				? vets.findById(input.vetId()).orElseThrow(() -> new EntityNotFoundException("Vet", input.vetId()))
				: null;
		if (!StringUtils.hasText(input.description())) {
			throw new InvalidInputException("description", "Description is required");
		}
		if (input.description().length() > 255) {
			throw new InvalidInputException("description", "Description is longer than 255 characters");
		}
		Visit visit = new Visit();
		if (input.date() != null) {
			visit.setDate(input.date());
		}
		visit.setDescription(input.description().trim());
		visit.setVet(vet);
		pet.addVisit(visit);
		Visit saved = visits.save(visit);
		events.publishEvent(new VisitBookedEvent(saved.getId()));
		return saved;
	}

}
