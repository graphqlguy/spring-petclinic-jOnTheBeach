package org.springframework.samples.petclinic.upstream;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One read operation of a REST service, which the schema exposes as a root field of the
 * same shape. The arguments of the field become the path variables and the query
 * parameters of the operation, and the answer of the service goes to the client as the
 * service sent it. A generator writes these operations from the documentation of the
 * service, together with the root fields and their types in the schema.
 *
 * @param field the root field on <code>Query</code>
 * @param path the path of the operation, with its path variables in braces
 * @param arguments the arguments of the field that the operation takes
 * @param fixed query parameters the operation always sends, such as the number of the
 * list that a field of its own reads
 * @param paging how the <code>page</code> and <code>size</code> arguments reach the
 * service. Null for a field without them.
 * @param result how the answer is reshaped for the schema, or null when the schema takes
 * it as it is
 */
public record RestOperation(String field, String path, List<Argument> arguments, Map<String, String> fixed,
		Paging paging, Result result) {

	public RestOperation {
		arguments = (arguments != null) ? arguments : List.of();
		fixed = (fixed != null) ? fixed : Map.of();
	}

	/**
	 * An argument of the field, and the parameter of the operation it fills.
	 *
	 * @param name the name of the argument in the schema
	 * @param parameter the name of the parameter in the operation, which differs when the
	 * service uses a Spanish name or one that GraphQL does not allow
	 * @param in whether the parameter is a path variable or a query parameter
	 * @param list whether the argument is a list, which the query repeats for each value
	 * @param values the value the service expects for each value of the argument, such as
	 * <code>1</code> for <code>true</code>. Null when the service takes the values as
	 * they are.
	 * @param dateFormat the pattern a date argument is written in, such as
	 * <code>dd/MM/yyyy</code>. Null for the ISO format, <code>2026-10-22</code>.
	 * @param template the text the parameter takes, with <code>{}</code> where the value
	 * of the argument goes as a JSON string, for a service that takes a JSON query in a
	 * parameter. Null when the parameter takes the value alone.
	 * @param upperCase true for a parameter that the service matches in capitals alone,
	 * such as a municipality that the Junta writes as <code>MÁLAGA</code>. Null or false
	 * sends the value as the client wrote it.
	 */
	public record Argument(String name, String parameter, Location in, boolean list, Map<String, String> values,
			String dateFormat, String template, Boolean upperCase) {

	}

	/**
	 * Where a parameter goes in the request.
	 */
	public enum Location {

		@JsonProperty("path")
		PATH,

		@JsonProperty("query")
		QUERY

	}

	/**
	 * How the <code>page</code> and <code>size</code> arguments of the field reach the
	 * service, which names its paging parameters in its own way.
	 *
	 * @param style how the service counts where a page starts
	 * @param start the query parameter that takes where the page starts, for example
	 * <code>offset</code> or <code>pagina</code>. Null for {@link Style#LIMIT}.
	 * @param size the query parameter that takes the number of results on a page, for
	 * example <code>limit</code> or <code>page_size</code>. Null for a service whose
	 * pages have a fixed size.
	 */
	public record Paging(Style style, String start, String size) {

		/**
		 * How a service counts where a page starts.
		 */
		public enum Style {

			/**
			 * The service takes the number of results to skip, which the page and size
			 * arguments give.
			 */
			@JsonProperty("offset")
			OFFSET,

			/**
			 * The service takes the number of the page, starting at 1, as the page
			 * argument does.
			 */
			@JsonProperty("page")
			PAGE,

			/**
			 * The service takes the number of the page, starting at 0.
			 */
			@JsonProperty("page-from-zero")
			PAGE_FROM_ZERO,

			/**
			 * The service takes a limit only, which the size argument fills.
			 */
			@JsonProperty("limit")
			LIMIT

		}

	}

	/**
	 * How an answer is reshaped for the schema.
	 */
	public enum Result {

		/**
		 * The service answers with an object whose keys are data, such as counts by year,
		 * and the schema has a list of entries with a key and a value.
		 */
		@JsonProperty("entries")
		ENTRIES

	}

}
