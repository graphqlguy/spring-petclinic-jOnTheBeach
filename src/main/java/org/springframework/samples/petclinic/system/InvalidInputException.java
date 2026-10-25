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
package org.springframework.samples.petclinic.system;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thrown when input breaks a rule of the clinic, such as a telephone number that is not
 * ten digits. Holds one message for each field that broke a rule.
 */
public class InvalidInputException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public InvalidInputException(String field, String message) {
		this(Map.of(field, message));
	}

	public InvalidInputException(Map<String, String> fieldErrors) {
		super(fieldErrors.entrySet()
			.stream()
			.map((error) -> error.getKey() + ": " + error.getValue())
			.collect(Collectors.joining(", ")));
		this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
	}

	/**
	 * The broken rules, keyed by the name of the input field, in the order they were
	 * found.
	 * @return the message for each field
	 */
	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}

}
