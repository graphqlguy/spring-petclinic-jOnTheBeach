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
package org.springframework.samples.petclinic.owner.visit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository class for <code>Visit</code> domain objects.
 */
public interface VisitRepository extends JpaRepository<Visit, Integer> {

	List<Visit> findByVetIdOrderByDateDescIdDesc(Integer vetId);

	/**
	 * The visits of many vets in one statement, newest first.
	 */
	@Query("SELECT visit FROM Visit visit WHERE visit.vet.id IN :vetIds ORDER BY visit.date DESC, visit.id DESC")
	List<Visit> findByVetIds(@Param("vetIds") Collection<Integer> vetIds);

	/**
	 * A visit with its pet, the pet's owner and type, and its vet, read in one statement,
	 * so that every field can be read after the transaction ends.
	 */
	@Query("""
			SELECT visit FROM Visit visit
			JOIN FETCH visit.pet pet JOIN FETCH pet.owner JOIN FETCH pet.type LEFT JOIN FETCH visit.vet
			WHERE visit.id = :id""")
	Optional<Visit> findWithDetailsById(@Param("id") Integer id);

}
