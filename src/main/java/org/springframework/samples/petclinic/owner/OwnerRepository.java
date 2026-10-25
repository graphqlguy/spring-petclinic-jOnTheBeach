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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository class for <code>Owner</code> domain objects. All method names are compliant
 * with Spring Data naming conventions so this interface can easily be extended for Spring
 * Data. See:
 * https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Wick Dynex
 */
public interface OwnerRepository extends JpaRepository<Owner, Integer> {

	/**
	 * Retrieve {@link Owner}s from the data store by last name, returning all owners
	 * whose last name <i>starts</i> with the given name.
	 * @param lastName Value to search for
	 * @return a Collection of matching {@link Owner}s (or an empty Collection if none
	 * found)
	 */
	Page<Owner> findByLastNameStartingWith(String lastName, Pageable pageable);

	/**
	 * Retrieve an {@link Owner} from the data store by id.
	 * <p>
	 * This method returns an {@link Optional} containing the {@link Owner} if found. If
	 * no {@link Owner} is found with the provided id, it will return an empty
	 * {@link Optional}.
	 * </p>
	 * @param id the id to search for
	 * @return an {@link Optional} containing the {@link Owner} if found, or an empty
	 * {@link Optional} if not found.
	 * @throws IllegalArgumentException if the id is null (assuming null is not a valid
	 * input for id)
	 */
	Optional<Owner> findById(Integer id);

	/**
	 * Owners whose last name starts with the given text, in any case, and who live in one
	 * of the given cities. A null leaves its condition out.
	 */
	@Query("""
			SELECT owner FROM Owner owner
			WHERE (:lastName IS NULL OR LOWER(owner.lastName) LIKE LOWER(CONCAT(:lastName, '%')))
			AND (:cities IS NULL OR owner.city IN :cities)""")
	Page<Owner> findWithFilters(@Param("lastName") String lastName, @Param("cities") Collection<String> cities,
			Pageable pageable);

	/**
	 * Every city an owner lives in, each spelling once.
	 */
	@Query("SELECT DISTINCT owner.city FROM Owner owner WHERE owner.city IS NOT NULL")
	List<String> findCities();

	/**
	 * Owners whose name contains the given text, in any case and with or without accents.
	 * The service passes the text in lower case and without accents, and the query folds
	 * the name the same way, for the accented letters of Spanish.
	 */
	@Query("""
			SELECT owner FROM Owner owner
			WHERE replace(replace(replace(replace(replace(replace(replace(
				LOWER(CONCAT(owner.firstName, ' ', owner.lastName)),
				'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n')
				LIKE CONCAT('%', :text, '%')
			ORDER BY owner.lastName, owner.firstName""")
	List<Owner> search(@Param("text") String text);

}
