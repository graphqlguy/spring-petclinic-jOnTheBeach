package org.springframework.samples.petclinic.upstream;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;

/**
 * Matches the text a client searches for against the texts of a record, for the services
 * that send a whole list and leave the search to the client.
 */
public final class SearchText {

	private final String search;

	private SearchText(String search) {
		this.search = (search != null) ? search.strip().toLowerCase(Locale.ROOT) : "";
	}

	/**
	 * Prepares a search. A null or blank search matches every record.
	 */
	public static SearchText of(String search) {
		return new SearchText(search);
	}

	/**
	 * Whether one of the texts contains the search, in upper or lower case.
	 */
	public boolean matches(String... texts) {
		return matches(Arrays.asList(texts));
	}

	/**
	 * Whether one of the texts contains the search, in upper or lower case. Null texts
	 * are skipped.
	 */
	public boolean matches(Collection<String> texts) {
		return this.search.isEmpty() || texts.stream()
			.filter(Objects::nonNull)
			.anyMatch((text) -> text.toLowerCase(Locale.ROOT).contains(this.search));
	}

}
