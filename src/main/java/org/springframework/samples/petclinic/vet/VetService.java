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
package org.springframework.samples.petclinic.vet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.LatencySimulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads vets and their specialties.
 */
@Service
@Transactional(readOnly = true)
public class VetService {

	private static final Logger logger = LoggerFactory.getLogger(VetService.class);

	private final VetRepository vets;

	private final LatencySimulator latency;

	VetService(VetRepository vets, LatencySimulator latency) {
		this.vets = vets;
		this.latency = latency;
	}

	/**
	 * Every vet, with their specialties.
	 */
	public List<Vet> findAll() {
		this.latency.pause();
		return new ArrayList<>(this.vets.findAll());
	}

	/**
	 * The vet with this id.
	 * @throws EntityNotFoundException when no vet has the id
	 */
	public Vet findById(Integer id) {
		this.latency.pause();
		return this.vets.findById(id).orElseThrow(() -> new EntityNotFoundException("Vet", id));
	}

	/**
	 * Vets whose first or last name contains the text, in any case.
	 */
	public List<Vet> search(String text) {
		this.latency.pause();
		return this.vets.search(text);
	}

	/**
	 * The vets who have one specialty. One SQL statement for each specialty.
	 */
	public List<Vet> findBySpecialty(Specialty specialty) {
		this.latency.pause();
		return this.vets.findBySpecialtyIds(List.of(specialty.getId()));
	}

	/**
	 * The vets of many specialties in one SQL statement, keyed by specialty id. A
	 * specialty without vets is missing from the map.
	 */
	public Map<Integer, List<Vet>> findVetsBySpecialtyIds(List<Integer> specialtyIds) {
		logger.info("Batch fetching vets for {} specialties", specialtyIds.size());
		this.latency.pause();
		Map<Integer, List<Vet>> vetsBySpecialtyId = new HashMap<>();
		for (Vet vet : this.vets.findBySpecialtyIds(specialtyIds)) {
			for (Specialty specialty : vet.getSpecialties()) {
				if (specialtyIds.contains(specialty.getId())) {
					vetsBySpecialtyId.computeIfAbsent(specialty.getId(), (id) -> new ArrayList<>()).add(vet);
				}
			}
		}
		return vetsBySpecialtyId;
	}

}
