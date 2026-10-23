package org.springframework.samples.petclinic.upstream;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueConversionTests {

	@Test
	void readsAListOfObjectsWithOneKeyAsTexts() {
		Object value = List.of(Map.of("organism", "Educación"), Map.of("organism", "Salud"));

		assertThat(ValueConversion.TEXT_LIST.apply(value)).isEqualTo(List.of("Educación", "Salud"));
	}

	@Test
	void readsAnObjectWhoseFirstValueIsNullAsNull() {
		// the Junta sends documents such as {"document": null} when a link is missing
		Map<String, Object> missing = new LinkedHashMap<>();
		missing.put("document", null);

		assertThat(ValueConversion.TEXT_LIST.apply(List.of(missing, Map.of("document", "https://example.org/1"))))
			.isEqualTo(Arrays.asList(null, "https://example.org/1"));
	}

	@Test
	void readsATextInALanguage() {
		assertThat(ValueConversion.TEXT_LIST.apply(Map.of("_value", "Perros", "_lang", "es")))
			.isEqualTo(List.of("Perros"));
	}

	@Test
	void readsADecimalComma() {
		assertThat(ValueConversion.DECIMAL_COMMA.apply("1,459")).isEqualTo(1.459);
		assertThat(ValueConversion.DECIMAL_COMMA.apply("14.925.217,81")).isEqualTo(14925217.81);
		assertThat(ValueConversion.DECIMAL_COMMA.apply("36.72")).isEqualTo(36.72);
	}

	@Test
	void wrapsASingleObjectInAList() {
		Map<String, String> municipality = Map.of("codigo", "JA01290022");

		assertThat(ValueConversion.LIST.apply(municipality)).isEqualTo(List.of(municipality));
		assertThat(ValueConversion.LIST.apply(List.of(municipality))).isEqualTo(List.of(municipality));
	}

}
