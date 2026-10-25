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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetService;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link VisitService} against the seed data.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class VisitServiceTests {

	@Autowired
	private VisitService visits;

	@Autowired
	private VetService vets;

	@Test
	void findByVetReturnsTheVetsVisitsNewestFirst() {
		Vet douglas = this.vets.findById(3);
		List<Visit> found = this.visits.findByVet(douglas);
		assertThat(found).isNotEmpty().allMatch((visit) -> visit.getVet().getId().equals(3));
		assertThat(found).extracting(Visit::getDate).isSortedAccordingTo((a, b) -> b.compareTo(a));
	}

	@Test
	void findVisitsByVetIdsMatchesOneCallPerVet() {
		List<Integer> ids = List.of(1, 2, 3, 4, 5, 6);
		Map<Integer, List<Visit>> batch = this.visits.findVisitsByVetIds(ids);
		for (Integer id : ids) {
			assertThat(batch.getOrDefault(id, List.of())).extracting(Visit::getId)
				.containsExactlyElementsOf(
						this.visits.findByVet(this.vets.findById(id)).stream().map(Visit::getId).toList());
		}
	}

	@Test
	void findVisitsReturnsTheVisitsBetweenTwoDates() {
		List<Visit> found = this.visits.findVisits(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 12, 31));
		assertThat(found).hasSize(3).extracting(Visit::getDate).isSorted();
	}

	@Test
	void addVisitBooksForTomorrowAndPublishesAnEvent(ApplicationEvents events) {
		Visit visit = this.visits.addVisit(new AddVisitInput(7, 1, null, "check-up"));
		assertThat(visit.getDate()).isEqualTo(LocalDate.now().plusDays(1));
		assertThat(visit.getPet().getName()).isEqualTo("Samantha");
		assertThat(events.stream(VisitBookedEvent.class)).containsExactly(new VisitBookedEvent(visit.getId()));
	}

	@Test
	void addVisitRefusesAMissingDescriptionAndUnknownIds() {
		assertThatExceptionOfType(InvalidInputException.class)
			.isThrownBy(() -> this.visits.addVisit(new AddVisitInput(7, null, null, " ")));
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> this.visits.addVisit(new AddVisitInput(999, null, null, "check-up")));
		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> this.visits.addVisit(new AddVisitInput(7, 99, null, "check-up")));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void findWithDetailsCanBeReadAfterTheTransaction() {
		Visit visit = this.visits.findWithDetails(1);
		assertThat(visit.getPet().getOwner().getLastName()).isNotBlank();
		assertThat(visit.getPet().getType().getName()).isNotBlank();
		assertThat(visit.getVet().getLastName()).isNotBlank();
	}

}
