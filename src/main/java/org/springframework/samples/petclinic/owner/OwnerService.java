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

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.samples.petclinic.system.EntityNotFoundException;
import org.springframework.samples.petclinic.system.InvalidInputException;
import org.springframework.samples.petclinic.system.LatencySimulator;
import org.springframework.samples.petclinic.system.SortOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Reads and changes owners, applying the rules the {@link Owner} entity declares.
 */
@Service
@Transactional(readOnly = true)
public class OwnerService {

	private final OwnerRepository owners;

	private final Validator validator;

	private final LatencySimulator latency;

	OwnerService(OwnerRepository owners, Validator validator, LatencySimulator latency) {
		this.owners = owners;
		this.validator = validator;
		this.latency = latency;
	}

	/**
	 * Every owner, ordered by last name and first name.
	 */
	public List<Owner> findAll() {
		latency.pause();
		return owners.findAll(Sort.by("lastName", "firstName"));
	}

	/**
	 * The owner with this id, or {@code null} when no owner has it.
	 */
	public Owner findById(Integer id) {
		latency.pause();
		return owners.findById(id).orElse(null);
	}

	/**
	 * One page of the owners that match the filter.
	 * @param filter the conditions, or {@code null} for every owner
	 * @param page the page number, counting from 0
	 * @param size the number of owners on a page
	 * @param sort the order, or {@code null} for last name ascending
	 */
	public OwnerPage findOwners(OwnerFilter filter, int page, int size, OwnerSort sort) {
		latency.pause();
		String lastName = (filter != null) ? blankToNull(filter.lastName()) : null;
		String city = (filter != null) ? blankToNull(filter.city()) : null;
		List<String> cities = (city != null) ? spellingsOf(city) : null;
		Page<Owner> result = owners.findWithFilters(lastName, cities, PageRequest.of(page, size, toSort(sort)));
		return new OwnerPage(result.getContent(), result.getTotalElements(), result.getTotalPages(),
				result.getNumber(), result.getSize(), result.isFirst(), result.isLast(), result.hasNext(),
				result.hasPrevious());
	}

	/**
	 * Owners whose first or last name contains the text, in any case.
	 */
	public List<Owner> search(String text) {
		latency.pause();
		return owners.search(text);
	}

	@Transactional
	public Owner addOwner(AddOwnerInput input) {
		latency.pause();
		Owner owner = new Owner();
		owner.setFirstName(input.firstName());
		owner.setLastName(input.lastName());
		owner.setAddress(input.address());
		owner.setCity(input.city());
		owner.setTelephone(input.telephone());
		validate(owner);
		return owners.save(owner);
	}

	/**
	 * Changes the fields the input sets, and keeps the rest.
	 * @throws EntityNotFoundException when no owner has the id
	 * @throws InvalidInputException when a changed field breaks a rule
	 */
	@Transactional
	public Owner updateOwner(UpdateOwnerInput input) {
		latency.pause();
		Owner owner = owners.findById(input.id())
			.orElseThrow(() -> new EntityNotFoundException("Owner", input.id()));
		applyIfPresent(input.firstName(), owner::setFirstName);
		applyIfPresent(input.lastName(), owner::setLastName);
		applyIfPresent(input.address(), owner::setAddress);
		applyIfPresent(input.city(), owner::setCity);
		applyIfPresent(input.telephone(), owner::setTelephone);
		validate(owner);
		return owner;
	}

	// The same rules the web form applies: the constraints on Owner and Person.
	private void validate(Owner owner) {
		Map<String, String> errors = new LinkedHashMap<>();
		validator.validate(owner)
			.stream()
			.sorted(Comparator.comparing((ConstraintViolation<Owner> violation) -> violation.getPropertyPath().toString()))
			.forEach((violation) -> errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
	}

	private static Sort toSort(OwnerSort sort) {
		OwnerSortField field = (sort != null && sort.field() != null) ? sort.field() : OwnerSortField.LAST_NAME;
		Sort.Direction direction = (sort != null && sort.order() == SortOrder.DESC) ? Sort.Direction.DESC
				: Sort.Direction.ASC;
		String property = switch (field) {
			case LAST_NAME -> "lastName";
			case FIRST_NAME -> "firstName";
			case CITY -> "city";
		};
		// the id breaks ties, so that each owner shows on exactly one page
		return Sort.by(direction, property).and(Sort.by("id"));
	}

	// The cities as the database spells them that match the given one in any case and
	// with or without accents, so that "malaga" finds the owners of "Málaga"
	private List<String> spellingsOf(String city) {
		String wanted = withoutAccents(city);
		return owners.findCities().stream().filter((stored) -> withoutAccents(stored).equals(wanted)).toList();
	}

	private static String withoutAccents(String text) {
		String decomposed = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String text) {
		return StringUtils.hasText(text) ? text.trim() : null;
	}

	private static <T> void applyIfPresent(T value, Consumer<T> setter) {
		if (value != null) {
			setter.accept(value);
		}
	}

}
