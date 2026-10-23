package org.springframework.samples.petclinic.cimavet;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.upstream.MockUpstreamTests;

/**
 * Queries CIMAVet, the register of veterinary medicines of the Spanish medicines agency,
 * through GraphQL, against the recorded answers.
 */
class CimaVetMockUpstreamTests extends MockUpstreamTests {

	@Test
	void medicinesByName() {
		this.graphQl.document("""
				{
				  cimaVetMedicines(name: "apoquel") {
				    totalCount
				    medicines { name activeIngredientNames prescription }
				  }
				}""")
			.execute()
			.path("cimaVetMedicines.totalCount")
			.entity(Integer.class)
			.isEqualTo(11)
			.path("cimaVetMedicines.medicines[0].name")
			.entity(String.class)
			.isEqualTo("APOQUEL 16 mg COMPRIMIDOS MASTICABLES PARA PERROS")
			.path("cimaVetMedicines.medicines[0].activeIngredientNames")
			.entity(String.class)
			.isEqualTo("OCLACITINIB MALEATO")
			.path("cimaVetMedicines.medicines[0].prescription")
			.entity(Boolean.class)
			.isEqualTo(true);
	}

}
