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
package org.springframework.samples.petclinic.owner.pet;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.system.LatencySimulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reads and changes pets, applying the rules of the web form: a name, a birth date that
 * is not in the future, and a name the owner does not already use for another pet.
 */
@Service
@Transactional(readOnly = true)
public class PetService {

	private final PetRepository pets;

	private final OwnerRepository owners;

	private final PetTypeRepository petTypes;

	private final LatencySimulator latency;

	PetService(PetRepository pets, OwnerRepository owners, PetTypeRepository petTypes, LatencySimulator latency) {
		this.pets = pets;
		this.owners = owners;
		this.petTypes = petTypes;
		this.latency = latency;
	}

	/**
	 * The pet with this id.
	 * @throws EntityNotFoundException when no pet has the id
	 */
	public Pet findById(Integer id) {
		latency.pause();
		return pets.findById(id).orElseThrow(() -> new EntityNotFoundException("Pet", id));
	}

	/**
	 * Every pet type, ordered by name.
	 */
	public List<PetType> findPetTypes() {
		latency.pause();
		return petTypes.findPetTypes();
	}

	/**
	 * Pets whose name contains the text, in any case.
	 */
	public List<Pet> search(String text) {
		latency.pause();
		return pets.search(text);
	}

	/**
	 * Registers a new pet for an owner.
	 * @throws EntityNotFoundException when the owner or the pet type does not exist
	 * @throws InvalidInputException when the name or the birth date breaks a rule
	 */
	@Transactional
	public Pet addPet(AddPetInput input) {
		latency.pause();
		Owner owner = owners.findById(input.ownerId())
			.orElseThrow(() -> new EntityNotFoundException("Owner", input.ownerId()));
		PetType type = findType(input.typeId());
		Map<String, String> errors = new LinkedHashMap<>();
		if (!StringUtils.hasText(input.name())) {
			errors.put("name", "Name is required");
		}
		else if (pets.existsByOwnerIdAndNameIgnoreCase(owner.getId(), input.name().trim())) {
			errors.put("name", duplicateName(owner, input.name()));
		}
		checkBirthDate(input.birthDate(), true, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
		Pet pet = new Pet();
		pet.setName(input.name().trim());
		pet.setBirthDate(input.birthDate());
		pet.setType(type);
		owner.addPet(pet);
		return pets.save(pet);
	}

	/**
	 * Changes the fields the input sets, and keeps the rest.
	 * @throws EntityNotFoundException when the pet or the pet type does not exist
	 * @throws InvalidInputException when the name or the birth date breaks a rule
	 */
	@Transactional
	public Pet updatePet(UpdatePetInput input) {
		latency.pause();
		Pet pet = pets.findById(input.id()).orElseThrow(() -> new EntityNotFoundException("Pet", input.id()));
		Map<String, String> errors = new LinkedHashMap<>();
		if (input.name() != null) {
			if (!StringUtils.hasText(input.name())) {
				errors.put("name", "Name is required");
			}
			else if (pets.existsByOwnerIdAndNameIgnoreCaseAndIdNot(pet.getOwner().getId(), input.name().trim(),
					pet.getId())) {
				errors.put("name", duplicateName(pet.getOwner(), input.name()));
			}
		}
		checkBirthDate(input.birthDate(), false, errors);
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
		if (input.name() != null) {
			pet.setName(input.name().trim());
		}
		if (input.birthDate() != null) {
			pet.setBirthDate(input.birthDate());
		}
		if (input.typeId() != null) {
			pet.setType(findType(input.typeId()));
		}
		return pet;
	}

	private PetType findType(Integer typeId) {
		return petTypes.findById(typeId).orElseThrow(() -> new EntityNotFoundException("PetType", typeId));
	}

	private static void checkBirthDate(LocalDate birthDate, boolean required, Map<String, String> errors) {
		if (birthDate == null) {
			if (required) {
				errors.put("birthDate", "Birth date is required");
			}
		}
		else if (birthDate.isAfter(LocalDate.now())) {
			errors.put("birthDate", "Birth date cannot be in the future");
		}
	}

	private static String duplicateName(Owner owner, String name) {
		return owner.getFirstName() + " " + owner.getLastName() + " already has a pet named " + name.trim();
	}

}
