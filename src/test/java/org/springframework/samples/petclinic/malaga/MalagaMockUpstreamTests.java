package org.springframework.samples.petclinic.malaga;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

/**
 * Queries the open data of the city of Málaga and the geocoder of OpenStreetMap through
 * the hand-written fields, against the recorded answers.
 */
class MalagaMockUpstreamTests extends MockUpstreamTests {

	@Test
	void parksAndFountainsNearAnOwner() {
		this.graphQl.document("""
				{
				  owners(filter: { lastName: "Navarro" }) {
				    content {
				      location { latitude longitude }
				      nearestDogParks(first: 2) { name latitude longitude distanceInMetres }
				      nearestDrinkingFountains(first: 2) { latitude distanceInMetres }
				    }
				  }
				}""")
			.execute()
			.path("owners.content[0].location.latitude")
			.entity(Double.class)
			.isEqualTo(36.7241212)
			.path("owners.content[0].nearestDogParks[*].name")
			.entityList(String.class)
			.containsExactly("Parque canino San Miguel", "Parque canino Parque de los Angeles")
			.path("owners.content[0].nearestDogParks[0].latitude")
			.entity(Double.class)
			.isEqualTo(36.73518941)
			.path("owners.content[0].nearestDogParks[0].distanceInMetres")
			.entity(Integer.class)
			.isEqualTo(1236)
			.path("owners.content[0].nearestDrinkingFountains[0].distanceInMetres")
			.entity(Integer.class)
			.isEqualTo(167);
	}

	@Test
	void busesOfALine() {
		this.graphQl.document("{ malagaBuses(line: \"1\") { line latitude longitude } }")
			.execute()
			.path("malagaBuses[0].line")
			.entity(String.class)
			.isEqualTo("1")
			.path("malagaBuses[0].latitude")
			.entity(Double.class)
			.isEqualTo(36.737747);
	}

}
