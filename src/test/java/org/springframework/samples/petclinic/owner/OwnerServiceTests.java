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
		assertThat(owners.findAll()).hasSize(15)
			.extracting(Owner::getLastName)
			.startsWith("Beach", "Black", "Coleman");
	}

	@Test
	void findByIdReturnsNullForAnUnknownId() {
		assertThat(owners.findById(1).getLastName()).isEqualTo("Franklin");
		assertThat(owners.findById(999)).isNull();
	}

	@Test
	void findOwnersFiltersByLastNameAndCityInAnyCase() {
		OwnerPage davises = owners.findOwners(new OwnerFilter("davi", null), 0, 10, null);
		assertThat(davises.content()).extracting(Owner::getFirstName).containsExactly("Betty", "Harold");

		OwnerPage torremolinos = owners.findOwners(new OwnerFilter(null, "TORREMOLINOS"), 0, 10, null);
		assertThat(torremolinos.totalElements()).isEqualTo(3);

		OwnerPage blank = owners.findOwners(new OwnerFilter(" ", ""), 0, 10, null);
		assertThat(blank.totalElements()).isEqualTo(15);
	}

	@Test
	void findOwnersMatchesTheCityWithOrWithoutAccents() {
		assertThat(owners.findOwners(new OwnerFilter(null, "Malaga"), 0, 10, null).content())
			.extracting(Owner::getLastName)
			.containsExactly("Navarro", "Ruiz");
		assertThat(owners.findOwners(new OwnerFilter(null, "MÁLAGA"), 0, 10, null).totalElements()).isEqualTo(2);

		owners.addOwner(new AddOwnerInput("Ana", "Lozano", "Calle Larios 3", "Malaga", "9521234567"));
		assertThat(owners.findOwners(new OwnerFilter(null, "Málaga"), 0, 10, null).totalElements()).isEqualTo(3);

		assertThat(owners.findOwners(new OwnerFilter(null, "Nerja"), 0, 10, null).totalElements()).isZero();
	}

	@Test
	void findOwnersPagesAndSorts() {
		OwnerPage first = owners.findOwners(null, 0, 6, null);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.isFirst()).isTrue();
		assertThat(first.hasNext()).isTrue();

		OwnerPage last = owners.findOwners(null, 2, 6, null);
		assertThat(last.content()).hasSize(3);
		assertThat(last.isLast()).isTrue();
		assertThat(last.hasPrevious()).isTrue();

		OwnerPage byCity = owners.findOwners(null, 0, 1, new OwnerSort(OwnerSortField.CITY, SortOrder.DESC));
		assertThat(byCity.content().get(0).getCity()).isEqualTo("Windsor");
	}

	@Test
	void searchMatchesFirstOrLastName() {
		assertThat(owners.search("beach")).extracting(Owner::getFirstName).containsExactly("J On The");
		assertThat(owners.search("ANTONIO")).extracting(Owner::getLastName).containsExactly("Ruiz");
	}

	@Test
	void addOwner() {
		Owner owner = owners
			.addOwner(new AddOwnerInput("Ana", "Lozano", "Calle Larios 3", "Málaga", "9521234567"));
		assertThat(owner.getId()).isNotNull();
		assertThat(owners.findAll()).hasSize(16);
	}

	@Test
	void addOwnerRefusesATelephoneThatIsNotTenDigits() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> owners.addOwner(new AddOwnerInput("Ana", "", "Calle Larios 3", "Málaga", "123")))
			.satisfies((ex) -> assertThat(ex.getFieldErrors()).containsOnlyKeys("lastName", "telephone"));
	}

	@Test
	void updateOwnerChangesOnlyTheFieldsItSets() {
		Owner owner = owners.updateOwner(new UpdateOwnerInput(1, null, null, null, "Nerja", null));
		assertThat(owner.getCity()).isEqualTo("Nerja");
		assertThat(owner.getLastName()).isEqualTo("Franklin");
	}

	@Test
	void updateOwnerRefusesAnUnknownId() {
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> owners.updateOwner(new UpdateOwnerInput(999, "X", null, null, null, null)));
	}

}
