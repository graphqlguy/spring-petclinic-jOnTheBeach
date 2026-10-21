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
package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.system.SortOrder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link OwnerService} against the seed data.
 */
@SpringBootTest
@Transactional
class OwnerServiceTests {

	@Autowired
	private OwnerService owners;

	@Test
	void findAllOrdersByLastName() {
		assertThat(this.owners.findAll()).hasSize(40)
			.extracting(Owner::getLastName)
			.startsWith("Alonso", "Beach", "Blanco");
	}

	@Test
	void findByIdReturnsNullForAnUnknownId() {
		assertThat(this.owners.findById(1).getLastName()).isEqualTo("Ruiz");
		assertThat(this.owners.findById(999)).isNull();
	}

	@Test
	void findOwnersFiltersByLastNameAndCityInAnyCase() {
		OwnerPage garcias = this.owners.findOwners(new OwnerFilter("garc", null), 0, 10, null);
		assertThat(garcias.content()).extracting(Owner::getFirstName).containsExactly("María", "Carmen");

		OwnerPage marbella = this.owners.findOwners(new OwnerFilter(null, "MARBELLA"), 0, 10, null);
		assertThat(marbella.totalElements()).isEqualTo(2);

		OwnerPage blank = this.owners.findOwners(new OwnerFilter(" ", ""), 0, 10, null);
		assertThat(blank.totalElements()).isEqualTo(40);
	}

	@Test
	void findOwnersPagesAndSorts() {
		OwnerPage first = this.owners.findOwners(null, 0, 15, null);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.isFirst()).isTrue();
		assertThat(first.hasNext()).isTrue();

		OwnerPage last = this.owners.findOwners(null, 2, 15, null);
		assertThat(last.content()).hasSize(10);
		assertThat(last.isLast()).isTrue();
		assertThat(last.hasPrevious()).isTrue();

		OwnerPage byCity = this.owners.findOwners(null, 0, 1, new OwnerSort(OwnerSortField.CITY, SortOrder.DESC));
		assertThat(byCity.content().get(0).getCity()).isEqualTo("Vélez-Málaga");
	}

	@Test
	void searchMatchesFirstOrLastName() {
		assertThat(this.owners.search("beach")).extracting(Owner::getFirstName).containsExactly("J On The");
		assertThat(this.owners.search("ANTONIO")).extracting(Owner::getLastName).containsExactly("Ruiz");
	}

	@Test
	void addOwner() {
		Owner owner = this.owners
			.addOwner(new AddOwnerInput("Ana", "Lozano", "Calle Larios 3", "Málaga", "9521234567"));
		assertThat(owner.getId()).isNotNull();
		assertThat(this.owners.findAll()).hasSize(41);
	}

	@Test
	void addOwnerRefusesATelephoneThatIsNotTenDigits() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> this.owners.addOwner(new AddOwnerInput("Ana", "", "Calle Larios 3", "Málaga", "123")))
			.satisfies((ex) -> assertThat(ex.getFieldErrors()).containsOnlyKeys("lastName", "telephone"));
	}

	@Test
	void updateOwnerChangesOnlyTheFieldsItSets() {
		Owner owner = this.owners.updateOwner(new UpdateOwnerInput(1, null, null, null, "Nerja", null));
		assertThat(owner.getCity()).isEqualTo("Nerja");
		assertThat(owner.getLastName()).isEqualTo("Ruiz");
	}

	@Test
	void updateOwnerRefusesAnUnknownId() {
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> this.owners.updateOwner(new UpdateOwnerInput(999, "X", null, null, null, null)));
	}

}
