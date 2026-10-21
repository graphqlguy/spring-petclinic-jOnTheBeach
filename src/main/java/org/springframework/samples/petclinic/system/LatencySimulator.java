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

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pauses every service call for {@code petclinic.demo.latency}, so that the cost of each
 * database round trip shows on a laptop as it would against a remote database. The
 * default, {@code 0s}, leaves every call as it is.
 */
@Component
public class LatencySimulator {

	private final Duration latency;

	LatencySimulator(@Value("${petclinic.demo.latency:0s}") Duration latency) {
		this.latency = latency;
	}

	public void pause() {
		if (this.latency.isZero()) {
			return;
		}
		try {
			Thread.sleep(this.latency);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
