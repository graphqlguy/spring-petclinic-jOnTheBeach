package org.springframework.samples.petclinic.junta;

import org.junit.jupiter.api.Test;

import org.springframework.graphql.execution.ErrorType;
import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Queries the open data API of the Junta de Andalucía through GraphQL, against the
 * recorded answers.
 */
class JuntaMockUpstreamTests extends MockUpstreamTests {

	@Test
	void museumsOfTheProvinceOfMalaga() {
		mockUpstreams.resetRequests();

		graphQl.document("""
				{
				  juntaMuseums(province: MALAGA, size: 5) {
				    total
				    results { name municipality }
				  }
				}""")
			.execute()
			.path("juntaMuseums.total")
			.entity(Integer.class)
			.isEqualTo(35)
			.path("juntaMuseums.results[0].name")
			.entity(String.class)
			.isEqualTo("Centro de Arte Contemporáneo de Málaga");

		// the enum value becomes the Spanish name, and the filters left out become "-"
		mockUpstreams.verify(
				getRequestedFor(urlPathEqualTo("/junta/museums/search")).withQueryParam("province", equalTo("Málaga"))
					.withQueryParam("municipality", equalTo("-"))
					.withQueryParam("size", equalTo("5")));
	}

	@Test
	void holidayCalendarOfAMunicipalityWrittenInLowerCase() {
		mockUpstreams.resetRequests();

		graphQl.document("""
				{
				  juntaHolidayCalendar(province: MALAGA, municipality: "Málaga", year: "2026") {
				    holidays { description kind }
				  }
				}""")
			.execute()
			.path("juntaHolidayCalendar.holidays[?(@.kind == 'LOCAL')].description")
			.entityList(String.class)
			.containsExactly("FIESTA LOCAL EN MÁLAGA (MÁLAGA)", "FIESTA LOCAL EN MÁLAGA (MÁLAGA)");

		// the Junta matches the municipality in capitals alone
		mockUpstreams.verify(getRequestedFor(urlPathEqualTo("/junta/work-calendar/get/search_calendar"))
			.withQueryParam("municipality", equalTo("MÁLAGA")));
	}

	@Test
	void anUnknownKindComesBackWithTheKindsTheJuntaTakes() {
		graphQl.document("{ juntaMuseums(kind: \"Museos\", size: 5) { total } }")
			.execute()
			.errors()
			.expect((error) -> error.getErrorType() == ErrorType.BAD_REQUEST
					&& error.getMessage().contains("permitted: '-', 'Colección Museográfica', 'Conjunto', 'Museo'"))
			.verify()
			.path("juntaMuseums")
			.valueIsNull();
	}

	@Test
	void statisticalActivityWithItsProducts() {
		graphQl.document("""
				{
				  juntaStatisticalActivities(title: "Actividades de I+D en Andalucía", size: 5) {
				    total
				    results { kinds periodicity products { title latestData } }
				  }
				}""")
			.execute()
			.path("juntaStatisticalActivities.total")
			.entity(Integer.class)
			.isEqualTo(1)
			// the dataset sends [{"type": "Estadística"}], which the field reads as texts
			.path("juntaStatisticalActivities.results[0].kinds")
			.entityList(String.class)
			.containsExactly("Estadística")
			.path("juntaStatisticalActivities.results[0].products[0].latestData")
			.entity(String.class)
			.isEqualTo("2024");
	}

	@Test
	void associationsOfMunicipalitiesOfMalaga() {
		graphQl.document("""
				{
				  juntaLocalEntities(kind: "MANCOMUNIDAD", province: "PROVINCIA MÁLAGA", size: 5) {
				    total
				    results { name president { office } municipalities { name } }
				  }
				}""")
			.execute()
			.path("juntaLocalEntities.total")
			.entity(Integer.class)
			.isEqualTo(2)
			.path("juntaLocalEntities.results[1].name")
			.entity(String.class)
			.isEqualTo("MANCOMUNIDAD DE MUNICIPIOS DE LA COSTA DEL SOL OCCIDENTAL")
			.path("juntaLocalEntities.results[1].president.office")
			.entity(String.class)
			.isEqualTo("PRESIDENTE/A")
			.path("juntaLocalEntities.results[1].municipalities[*].name")
			.entityList(String.class)
			.contains("MARBELLA", "TORREMOLINOS");
	}

}
