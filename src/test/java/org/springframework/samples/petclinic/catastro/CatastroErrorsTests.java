package org.springframework.samples.petclinic.catastro;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatastroErrorsTests {

	@Test
	void namesTheFieldThatListsTheProvinces() {
		Object answer = Map.of("consulta_dnp",
				Map.of("lerr", Map.of("err", Map.of("cod", "12", "des", "LA PROVINCIA NO EXISTE"))));

		assertThat(CatastroConfiguration.errors(answer)).containsExactly(
				"The Catastro, the land registry of Spain, answered: LA PROVINCIA NO EXISTE. catastroProvinces lists the provinces as the Catastro writes them, such as MALAGA");
	}

	@Test
	void readsSeveralErrors() {
		Object answer = Map.of("consulta_callejero",
				Map.of("lerr",
						Map.of("err", List.of(Map.of("cod", "10", "des", "NO HAY COINCIDENCIAS EN LA BÚSQUEDA DE VÍAS"),
								Map.of("cod", "22", "des", "EL MUNICIPIO NO EXISTE.")))));

		assertThat(CatastroConfiguration.errors(answer)).containsExactly(
				"The Catastro, the land registry of Spain, answered: NO HAY COINCIDENCIAS EN LA BÚSQUEDA DE VÍAS",
				"The Catastro, the land registry of Spain, answered: EL MUNICIPIO NO EXISTE. catastroMunicipalities lists the municipalities of a province as the Catastro writes them, such as MALAGA or TORREMOLINOS");
	}

	@Test
	void findsNothingInAnAnswerWithoutErrors() {
		assertThat(CatastroConfiguration.errors(Map.of("consulta_dnp", Map.of("control", Map.of("cudnp", "1")))))
			.isEmpty();
	}

}
