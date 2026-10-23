package org.springframework.samples.petclinic.upstream;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLTypeUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.upstream.RestOperation.Argument;
import org.springframework.samples.petclinic.upstream.RestOperation.Location;
import org.springframework.samples.petclinic.upstream.RestOperation.Paging;
import org.springframework.samples.petclinic.upstream.RestOperation.Result;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fetches a root field that maps to one {@link RestOperation}: it builds the request from
 * the arguments, calls the service, and returns the answer as the service sent it. Each
 * call is logged with its URL and what came back, in the same way as the SOAP calls of
 * the land registry, so a demo can show that the live service answered.
 * <p>
 * An answer of <code>404 Not Found</code> or <code>204 No Content</code> becomes null. An
 * answer of <code>400 Bad Request</code> or <code>422 Unprocessable Content</code>
 * becomes an error that repeats the reason the service gave, such as the values an
 * argument takes, so a client can correct its arguments.
 */
public class RestOperationFetcher implements DataFetcher<Object> {

	private static final Log logger = LogFactory.getLog(RestOperationFetcher.class);

	private static final int MAX_REASON_LENGTH = 500;

	/**
	 * The keys under which the services send the list of a search, for the log.
	 */
	private static final List<String> LIST_KEYS = List.of("resultados", "results", "data", "features", "items");

	/**
	 * The keys under which the services send the number of results across all pages, for
	 * the log.
	 */
	private static final List<String> COUNT_KEYS = List.of("totalFilas", "count", "total");

	private final String upstream;

	private final String baseUrl;

	private final RestClient client;

	private final RestOperation operation;

	private final JsonMapper mapper;

	/**
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the URL the paths of the operations start from
	 * @param client the client that calls the service
	 * @param operation the operation behind the field
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public RestOperationFetcher(String upstream, String baseUrl, RestClient client, RestOperation operation,
			JsonMapper mapper) {
		this.upstream = upstream;
		this.baseUrl = baseUrl;
		this.client = client;
		this.operation = operation;
		this.mapper = mapper;
	}

	@Override
	public Object get(DataFetchingEnvironment environment) {
		URI uri = uri(environment);
		long started = System.nanoTime();
		ResponseEntity<String> answer = RestUpstreams.call(this.upstream, () -> read(uri));
		Object body = parse(answer.getBody(), environment);
		logger.info("REST GET %s returned %s (%d ms)".formatted(uri, summary(answer, body, environment),
				Duration.ofNanos(System.nanoTime() - started).toMillis()));
		return (this.operation.result() == Result.ENTRIES) ? entries(body) : body;
	}

	/**
	 * Reads the JSON of an answer. A few operations answer with plain text under the JSON
	 * content type, such as the citation of a download, and a field of type
	 * <code>String</code> takes that text as it is.
	 */
	private Object parse(String body, DataFetchingEnvironment environment) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			return this.mapper.readValue(body, Object.class);
		}
		catch (JacksonException ex) {
			if (GraphQLTypeUtil.unwrapAll(environment.getFieldType()) == Scalars.GraphQLString) {
				return body.strip();
			}
			throw new UpstreamException(this.upstream, "The answer of %s is not JSON".formatted(this.operation.path()));
		}
	}

	/**
	 * Turns a map from an answer of the service, such as counts by year, into the list of
	 * entries that the schema has, each with a key and a value.
	 */
	static List<Map<String, Object>> entries(Object answer) {
		if (!(answer instanceof Map<?, ?> map)) {
			return null;
		}
		List<Map<String, Object>> entries = new ArrayList<>();
		map.forEach((key, value) -> {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("key", String.valueOf(key));
			entry.put("value", value);
			entries.add(entry);
		});
		return entries;
	}

	private URI uri(DataFetchingEnvironment environment) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(this.baseUrl).path(this.operation.path());
		// every value travels as a URI variable, so the builder encodes it in full: a
		// plus sign in a search text stays a plus sign
		Map<String, Object> variables = new HashMap<>();
		for (Argument argument : this.operation.arguments()) {
			Object value = environment.getArgument(argument.name());
			if (value == null) {
				continue;
			}
			if (argument.in() == Location.PATH) {
				variables.put(argument.parameter(), parameterValue(argument, value));
			}
			else {
				List<?> values = argument.list() ? (List<?>) value : List.of(value);
				for (Object item : values) {
					query(builder, variables, argument.parameter(), parameterValue(argument, item));
				}
			}
		}
		this.operation.fixed().forEach((parameter, value) -> query(builder, variables, parameter, value));
		Paging paging = this.operation.paging();
		if (paging != null) {
			// pages count from 0, as they do for owners and pets
			int size = ResultPages.pageSize(environment.getArgumentOrDefault("size", 10));
			int page = ResultPages.pageNumber(environment.getArgumentOrDefault("page", 0));
			switch (paging.style()) {
				case OFFSET -> query(builder, variables, paging.start(), (long) page * size);
				case PAGE -> query(builder, variables, paging.start(), page + 1);
				case PAGE_FROM_ZERO -> query(builder, variables, paging.start(), page);
				case LIMIT -> {
					// the service returns the first page only
				}
			}
			// a service with pages of a fixed size takes the page number alone
			if (paging.size() != null) {
				query(builder, variables, paging.size(), size);
			}
		}
		return builder.encode().buildAndExpand(variables).toUri();
	}

	/**
	 * The value the service expects for a value of an argument: the one the operation
	 * table gives for it, a date in the pattern of the service, the value inside a JSON
	 * query, or the value as it is, in capitals when the service matches capitals alone.
	 */
	private Object parameterValue(Argument argument, Object value) {
		if (Boolean.TRUE.equals(argument.upperCase())) {
			return String.valueOf(value).toUpperCase(Locale.ROOT);
		}
		if (argument.template() != null) {
			return argument.template().replace("{}", this.mapper.writeValueAsString(String.valueOf(value)));
		}
		if (argument.values() != null) {
			return argument.values().getOrDefault(String.valueOf(value), String.valueOf(value));
		}
		if (argument.dateFormat() != null && value instanceof LocalDate date) {
			return date.format(DateTimeFormatter.ofPattern(argument.dateFormat()));
		}
		return value;
	}

	private static void query(UriComponentsBuilder builder, Map<String, Object> variables, String parameter,
			Object value) {
		String variable = "query" + variables.size();
		builder.queryParam(parameter, "{" + variable + "}");
		variables.put(variable, value);
	}

	private ResponseEntity<String> read(URI uri) {
		try {
			return this.client.get().uri(uri).retrieve().toEntity(String.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return ResponseEntity.notFound().build();
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode().value() != 400 && ex.getStatusCode().value() != 422) {
				throw ex;
			}
			// the client gets the reason, and the log keeps the request it was for
			logger.info("REST GET %s was rejected: %s".formatted(uri, reason(ex)));
			throw new InvalidArgumentException("%s rejected the arguments: %s".formatted(this.upstream, reason(ex)));
		}
	}

	private static String reason(HttpClientErrorException ex) {
		String text = ex.getResponseBodyAsString().replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
		return (text.length() > MAX_REASON_LENGTH) ? text.substring(0, MAX_REASON_LENGTH) + "..." : text;
	}

	private static String summary(ResponseEntity<String> answer, Object body, DataFetchingEnvironment environment) {
		if (body == null) {
			return "nothing (" + HttpStatus.valueOf(answer.getStatusCode().value()) + ")";
		}
		if (body instanceof Map<?, ?> map) {
			Object results = LIST_KEYS.stream().map(map::get).filter(List.class::isInstance).findFirst().orElse(null);
			Object count = COUNT_KEYS.stream().map(map::get).filter(Number.class::isInstance).findFirst().orElse(null);
			if (results instanceof List<?> list) {
				return (count instanceof Number total) ? "%d of %,d results".formatted(list.size(), total.longValue())
						: list.size() + " results";
			}
		}
		if (body instanceof List<?> list) {
			return list.size() + " items";
		}
		if (body instanceof Map<?, ?>) {
			return "one " + ((GraphQLNamedType) GraphQLTypeUtil.unwrapAll(environment.getFieldType())).getName();
		}
		return "the value " + body;
	}

}
