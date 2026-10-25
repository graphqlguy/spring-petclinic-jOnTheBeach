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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link VetService} against the seed data.
 */
@SpringBootTest
@Transactional
class VetServiceTests {

	private static final int RADIOLOGY = 1;

	private static final int SURGERY = 2;

	private static final int DENTISTRY = 3;

	@Autowired
	private VetService vets;

	@Test
	void findAllReturnsTheSixVets() {
		assertThat(vets.findAll()).hasSize(6);
	}

	@Test
	void findByIdRefusesAnUnknownId() {
		assertThat(vets.findById(2).getLastName()).isEqualTo("Leary");
		assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> vets.findById(99));
	}

	@Test
	void findVetsBySpecialtyIdsGroupsBySpecialty() {
		Map<Integer, List<Vet>> bySpecialty = vets.findVetsBySpecialtyIds(List.of(RADIOLOGY, SURGERY, DENTISTRY));
		assertThat(bySpecialty.get(RADIOLOGY)).extracting(Vet::getLastName)
			.containsExactlyInAnyOrder("Leary", "Stevens");
		assertThat(bySpecialty.get(SURGERY)).extracting(Vet::getLastName)
			.containsExactlyInAnyOrder("Douglas", "Ortega");
		assertThat(bySpecialty.get(DENTISTRY)).extracting(Vet::getLastName).containsExactly("Douglas");
	}

	@Test
	void searchMatchesFirstOrLastName() {
		assertThat(vets.search("HELEN")).extracting(Vet::getLastName).containsExactly("Leary");
		assertThat(vets.search("helen lea")).extracting(Vet::getLastName).containsExactly("Leary");
	}

}
