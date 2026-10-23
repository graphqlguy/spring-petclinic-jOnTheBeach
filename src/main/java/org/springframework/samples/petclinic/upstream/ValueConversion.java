package org.springframework.samples.petclinic.upstream;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How a value in an answer of a Spanish service is converted for the schema. A value that
 * cannot be converted, such as an empty text where a number belongs, becomes null. A list
 * is converted one value at a time.
 */
public enum ValueConversion {

	/**
	 * A date sent as milliseconds since 1970, as the medicines agency sends them, read as
	 * the day it falls on in Madrid.
	 */
	@JsonProperty("epoch-millis-date")
	EPOCH_MILLIS_DATE {
		@Override
		Object convert(Object value) {
			return (value instanceof Number millis)
					? Instant.ofEpochMilli(millis.longValue()).atZone(MADRID).toLocalDate() : null;
		}
	},

	/**
	 * A moment sent as milliseconds since 1970, read as the time in Madrid.
	 */
	@JsonProperty("epoch-millis-date-time")
	EPOCH_MILLIS_DATE_TIME {
		@Override
		Object convert(Object value) {
			return (value instanceof Number millis)
					? Instant.ofEpochMilli(millis.longValue()).atZone(MADRID).toOffsetDateTime() : null;
		}
	},

	/**
	 * A yes or a no sent as text, such as <code>Si</code>, <code>No</code>,
	 * <code>S</code>, <code>N</code>, <code>1</code> or <code>0</code>.
	 */
	@JsonProperty("yes-no")
	YES_NO {
		@Override
		Object convert(Object value) {
			if (value instanceof Boolean yes) {
				return yes;
			}
			String text = value.toString().strip().toLowerCase(Locale.ROOT);
			if (YES.contains(text)) {
				return true;
			}
			return NO.contains(text) ? false : null;
		}
	},

	/**
	 * A number sent as text with a decimal comma, such as <code>1,459</code> for a price
	 * in euros. A text with a decimal point, which the same datasets send at times, is
	 * read as it is.
	 */
	@JsonProperty("decimal-comma")
	DECIMAL_COMMA {
		@Override
		Object convert(Object value) {
			if (value instanceof Number number) {
				return number.doubleValue();
			}
			String text = value.toString().strip();
			return NUMBER.convert(text.contains(",") ? text.replace(".", "").replace(',', '.') : text);
		}
	},

	/**
	 * A number sent as text, such as <code>36.72</code>.
	 */
	@JsonProperty("number")
	NUMBER {
		@Override
		Object convert(Object value) {
			if (value instanceof Number number) {
				return number.doubleValue();
			}
			try {
				return Double.valueOf(value.toString().strip());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
	},

	/**
	 * A whole number sent as text, such as <code>29067</code>.
	 */
	@JsonProperty("integer")
	INTEGER {
		@Override
		Object convert(Object value) {
			if (value instanceof Number number) {
				return number.intValue();
			}
			try {
				return Integer.valueOf(value.toString().strip());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
	},

	/**
	 * A list of texts, which some datasets send as one text, as a list of texts, as a
	 * list of objects with one key each, such as
	 * <code>[{"organism": "Educación"}]</code>, or as texts in a language, such as
	 * <code>[{"_value": "Perros", "_lang": "es"}]</code>.
	 */
	@JsonProperty("text-list")
	TEXT_LIST {
		@Override
		public Object apply(Object value) {
			if (value == null) {
				return null;
			}
			List<?> values = (value instanceof List<?> list) ? list : List.of(value);
			return values.stream().map(this::convert).toList();
		}

		@Override
		Object convert(Object value) {
			Object text = value;
			if (value instanceof Map<?, ?> map) {
				text = map.containsKey("_value") ? map.get("_value")
						: (map.isEmpty() ? null : map.values().iterator().next());
			}
			return (text != null) ? text.toString() : null;
		}
	},

	/**
	 * A list that the service sends as a single object when it holds one element, as
	 * answers converted from XML do.
	 */
	@JsonProperty("list")
	LIST {
		@Override
		public Object apply(Object value) {
			return (value == null || value instanceof List<?>) ? value : List.of(value);
		}

		@Override
		Object convert(Object value) {
			return value;
		}
	},

	/**
	 * An object whose values are the parts of one text, such as the five parts of a
	 * cadastral reference, read as the parts joined in order.
	 */
	@JsonProperty("joined")
	JOINED {
		@Override
		Object convert(Object value) {
			if (value instanceof Map<?, ?> map) {
				StringBuilder text = new StringBuilder();
				map.values().forEach(text::append);
				return text.toString();
			}
			return value.toString();
		}
	},

	/**
	 * An object whose keys are data, such as <code>{"1300": "Ley"}</code>, read as a list
	 * of entries with a key and a value.
	 */
	@JsonProperty("entries")
	ENTRIES {
		@Override
		public Object apply(Object value) {
			return RestOperationFetcher.entries(value);
		}

		@Override
		Object convert(Object value) {
			return value;
		}
	},

	/**
	 * An id that some answers send as a number, such as <code>135.0</code>, read as text
	 * without a decimal part, such as <code>135</code>. A text is read as it is.
	 */
	@JsonProperty("identifier")
	IDENTIFIER {
		@Override
		Object convert(Object value) {
			if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
				return String.valueOf(number.longValue());
			}
			return value.toString();
		}
	},

	/**
	 * Any value as text, for a key that holds a number in some answers and a text in
	 * others. Spaces at both ends are removed, and an empty text becomes null.
	 */
	@JsonProperty("text")
	TEXT {
		@Override
		Object convert(Object value) {
			String text = value.toString().strip();
			return text.isEmpty() ? null : text;
		}
	};

	private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

	private static final Set<String> YES = Set.of("si", "sí", "s", "1", "true", "yes", "y");

	private static final Set<String> NO = Set.of("no", "n", "0", "false");

	/**
	 * Converts a value, or each value of a list.
	 */
	public Object apply(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(this::apply).toList();
		}
		return convert(value);
	}

	abstract Object convert(Object value);

}
