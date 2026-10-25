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

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository class for <code>Pet</code> domain objects.
 */
public interface PetRepository extends JpaRepository<Pet, Integer> {

	/**
	 * Pets whose name contains the given text, in any case.
	 */
	@Query("SELECT pet FROM Pet pet WHERE LOWER(pet.name) LIKE LOWER(CONCAT('%', :text, '%')) ORDER BY pet.name")
	List<Pet> search(@Param("text") String text);

	boolean existsByOwnerIdAndNameIgnoreCase(Integer ownerId, String name);

	boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Integer ownerId, String name, Integer id);

}
