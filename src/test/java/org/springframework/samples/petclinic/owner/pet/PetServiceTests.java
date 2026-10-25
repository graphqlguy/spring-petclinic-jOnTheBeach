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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link PetService} against the seed data.
 */
@SpringBootTest
@Transactional
class PetServiceTests {

	private static final int DOG = 2;

	@Autowired
	private PetService pets;

	@Autowired
	private OwnerService owners;

	@Test
	void findPetTypesOrdersByName() {
		assertThat(pets.findPetTypes()).extracting(PetType::getName)
			.containsExactly("bird", "cat", "dog", "hamster", "lizard", "snake");
	}

	@Test
	void findByIdRefusesAnUnknownId() {
		assertThat(pets.findById(7).getName()).isEqualTo("Samantha");
		assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> pets.findById(999));
	}

	@Test
	void addPetGivesThePetToItsOwner() {
		Pet pet = pets.addPet(new AddPetInput(1, "Bobby", LocalDate.of(2024, 5, 1), DOG));
		assertThat(pet.getId()).isNotNull();
		assertThat(pet.getOwner().getId()).isEqualTo(1);
		assertThat(owners.findById(1).getPets()).extracting(Pet::getName).contains("Bobby", "Leo");
	}

	@Test
	void addPetRefusesADuplicateNameAndAFutureBirthDate() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> pets.addPet(new AddPetInput(1, "leo", LocalDate.now().plusDays(1), DOG)))
			.satisfies((ex) -> assertThat(ex.getFieldErrors()).containsOnlyKeys("name", "birthDate")
				.containsEntry("name", "George Franklin already has a pet named leo"));
	}

	@Test
	void addPetRefusesAnUnknownOwnerOrType() {
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> pets.addPet(new AddPetInput(999, "Bobby", LocalDate.of(2024, 5, 1), DOG)));
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> pets.addPet(new AddPetInput(1, "Bobby", LocalDate.of(2024, 5, 1), 99)));
	}

	@Test
	void updatePetChangesOnlyTheFieldsItSets() {
		Pet pet = pets.updatePet(new UpdatePetInput(7, "Samantha II", null, null));
		assertThat(pet.getName()).isEqualTo("Samantha II");
		assertThat(pet.getType().getName()).isEqualTo("cat");
	}

	@Test
	void updatePetRefusesTheNameOfASibling() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> pets.updatePet(new UpdatePetInput(7, "Max", null, null)));
	}

	@Test
	void searchMatchesPartOfTheName() {
		assertThat(pets.search("WOOF")).extracting(Pet::getName).containsExactly("Woofy");
		assertThat(pets.search("boqueron")).extracting(Pet::getName).containsExactly("Boquerón");
	}

}
