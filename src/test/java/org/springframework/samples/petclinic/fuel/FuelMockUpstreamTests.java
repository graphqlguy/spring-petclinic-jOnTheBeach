package org.springframework.samples.petclinic.fuel;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

/**
 * Queries the fuel prices of the Ministry for the Ecological Transition through GraphQL,
 * against the recorded answers.
 */
class FuelMockUpstreamTests extends MockUpstreamTests {

	@Test
	void stationsOfTorremolinosWithTheirPrices() {
		graphQl.document("""
				{
				  fuelStationsInMunicipality(municipalityId: "4544") {
				    stations { brand dieselAPrice gasoline95E5Price }
				  }
				}""")
			.execute()
			.path("fuelStationsInMunicipality.stations")
			.entityList(Object.class)
			.hasSize(12)
			.path("fuelStationsInMunicipality.stations[0].brand")
			.entity(String.class)
			.isEqualTo("CARREFOUR")
			// the ministry writes the price as 1,968
			.path("fuelStationsInMunicipality.stations[0].dieselAPrice")
			.entity(Double.class)
			.isEqualTo(1.968);
	}

}
