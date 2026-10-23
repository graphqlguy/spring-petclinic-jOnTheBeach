package org.springframework.samples.petclinic.catastro;

import org.junit.jupiter.api.Test;

import org.springframework.graphql.execution.ErrorType;
import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Queries the SOAP services of the Spanish land registry through GraphQL, against the
 * recorded answers.
 */
class CatastroMockUpstreamTests extends MockUpstreamTests {

	@Test
	void unitOfAParcelByItsReference() {
		this.mockUpstreams.resetRequests();

		this.graphQl.document("""
				{
				  catastroProperty(reference: "3148502UF7634N0001BU") {
				    unit { use surfaceSquareMetres yearBuilt }
				  }
				}""")
			.execute()
			.path("catastroProperty.unit.use")
			.entity(String.class)
			.isEqualTo("Comercial")
			.path("catastroProperty.unit.surfaceSquareMetres")
			.entity(Integer.class)
			.isEqualTo(612)
			.path("catastroProperty.unit.yearBuilt")
			.entity(Integer.class)
			.isEqualTo(1974);

		this.mockUpstreams.verify(postRequestedFor(urlPathEqualTo("/catastro/callejero"))
			.withHeader("SOAPAction", containing("Consulta_DNPRC"))
			.withRequestBody(containing(">3148502UF7634N0001BU<")));
	}

	@Test
	void reportsTheErrorOfTheCatastroAlsoWhenNoErrorFieldIsSelected() {
		this.graphQl.document("""
				{
				  catastroStreets(province: "MALAGA", municipality: "MALAGA", name: "ZZZZZZ") {
				    streets { name }
				    errorCount
				  }
				}""")
			.execute()
			.errors()
			.expect((error) -> error.getErrorType() == ErrorType.BAD_REQUEST && error.getMessage()
				.equals("The Catastro, the land registry of Spain, answered: NO HAY COINCIDENCIAS EN LA BÚSQUEDA DE VÍAS"))
			.verify()
			.path("catastroStreets.errorCount")
			.entity(Integer.class)
			.isEqualTo(1);
	}

	@Test
	void parcelAtAPoint() {
		this.graphQl.document("""
				{
				  catastroParcelAt(latitude: 36.7188122, longitude: -4.4216645) {
				    parcels { reference address }
				  }
				}""")
			.execute()
			.path("catastroParcelAt.parcels[0].reference")
			.entity(String.class)
			.isEqualTo("3148502UF7634N")
			.path("catastroParcelAt.parcels[0].address")
			.entity(String.class)
			.isEqualTo("CL MARQUES DE LARIOS 12 MALAGA (MÁLAGA)");
	}

}
