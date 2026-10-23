package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.Resource;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.samples.petclinic.upstream.RestOperationTable.FieldSource;
import org.springframework.samples.petclinic.upstream.SoapOperation.Part;

/**
 * The operations of one SOAP service, as a generator wrote them from the service's WSDL
 * into a JSON file next to the schema, with the fields that read the answers.
 *
 * @param operations the operations, one for each root field
 * @param fields the fields whose name in the schema differs from the element in the
 * answers, or whose value is converted, by type
 */
public record SoapOperationTable(List<SoapOperation> operations, Map<String, Map<String, FieldSource>> fields) {

	private static final Log logger = LogFactory.getLog(SoapOperationTable.class);

	public SoapOperationTable {
		fields = (fields != null) ? fields : Map.of();
	}

	/**
	 * A client that sends the body of an operation and returns the first element of the
	 * body of the answer, read as nested maps.
	 */
	@FunctionalInterface
	public interface SoapCaller {

		Object call(String service, String action, Map<String, String> parts);

	}

	/**
	 * Reads the errors that a service reports inside an answer, such as an unknown
	 * street, as messages a client can act on.
	 */
	@FunctionalInterface
	public interface ErrorReader {

		List<String> messages(Object answer);

	}

	public static SoapOperationTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, SoapOperationTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the operations in " + resource, ex);
		}
	}

	/**
	 * Registers a fetcher for each root field, and a fetcher for each field that reads
	 * another element or converts its value. A root field whose answer reports errors
	 * returns the answer with a GraphQL error for each of them.
	 */
	public void register(RuntimeWiring.Builder wiring, SoapCaller caller, ErrorReader errors) {
		wiring.type("Query", (type) -> {
			operations
				.forEach((operation) -> type.dataFetcher(operation.field(), fetcher(operation, caller, errors)));
			return type;
		});
		fields.forEach((typeName, typeFields) -> wiring.type(typeName, (type) -> {
			typeFields.forEach(
					(field, source) -> type.dataFetcher(field, (environment) -> source.read(environment.getSource())));
			return type;
		}));
	}

	private static DataFetcher<Object> fetcher(SoapOperation operation, SoapCaller caller, ErrorReader errors) {
		return (environment) -> {
			Map<String, String> parts = new LinkedHashMap<>();
			for (Part part : operation.parts()) {
				Object value = (part.argument() != null) ? environment.getArgument(part.argument()) : null;
				String text = (value != null) ? String.valueOf(value) : part.value();
				if (value != null && part.values() != null) {
					// a word such as Calle finds its code as CALLE does
					text = part.values()
						.getOrDefault(text, part.values().getOrDefault(text.strip().toUpperCase(Locale.ROOT), text));
				}
				parts.put(part.element(), (text != null) ? text : "");
			}
			long started = System.nanoTime();
			Object answer = caller.call(operation.service(), operation.action(), parts);
			logger.info("SOAP %s %s returned %s (%d ms)".formatted(operation.action(), parts.values(),
					(answer instanceof Map<?, ?> map) ? map.keySet() : answer,
					Duration.ofNanos(System.nanoTime() - started).toMillis()));
			List<String> messages = errors.messages(answer);
			if (messages.isEmpty()) {
				return answer;
			}
			// the answer keeps what it holds, such as the numbers nearest to a missing
			// street number, and each error tells the client why the answer holds less
			// than it asked for, whatever fields it selected
			return DataFetcherResult.newResult()
				.data(answer)
				.errors(messages.stream()
					.map((message) -> GraphqlErrorBuilder.newError(environment)
						.errorType(ErrorType.BAD_REQUEST)
						.message(message)
						.build())
					.toList())
				.build();
		};
	}

}
