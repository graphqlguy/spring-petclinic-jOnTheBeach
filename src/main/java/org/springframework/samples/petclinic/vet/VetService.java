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
import java.util.List;

import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.LatencySimulator;
import org.springframework.samples.petclinic.system.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads vets and their specialties.
 */
@Service
@Transactional(readOnly = true)
public class VetService {

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
		latency.pause();
		return new ArrayList<>(vets.findAll());
	}

	/**
	 * The vet with this id.
	 * @throws EntityNotFoundException when no vet has the id
	 */
	public Vet findById(Integer id) {
		latency.pause();
		return vets.findById(id).orElseThrow(() -> new EntityNotFoundException("Vet", id));
	}

	/**
	 * Vets whose first or last name contains the text, in any case and with or without
	 * accents.
	 */
	public List<Vet> search(String text) {
		latency.pause();
		return vets.search(Text.withoutAccents(text));
	}

}
