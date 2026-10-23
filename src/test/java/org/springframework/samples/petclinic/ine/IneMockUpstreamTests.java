package org.springframework.samples.petclinic.ine;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

/**
 * Queries the National Statistics Institute through GraphQL, against the recorded
 * answers.
 */
class IneMockUpstreamTests extends MockUpstreamTests {

	@Test
	void populationOfTheCityOfMalaga() {
		this.graphQl.document("""
				{
				  ineSeriesData(code: "DPOP13663", last: 5) {
				    name unit data { year value }
				  }
				}""")
			.execute()
			.path("ineSeriesData.unit")
			.entity(String.class)
			.isEqualTo("Persons")
			.path("ineSeriesData.data[*].year")
			.entityList(Integer.class)
			.containsExactly(2021, 2022, 2023, 2024, 2025)
			.path("ineSeriesData.data[4].value")
			.entity(Double.class)
			.isEqualTo(599063.0);
	}

}
