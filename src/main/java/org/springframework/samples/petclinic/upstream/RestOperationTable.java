package org.springframework.samples.petclinic.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import graphql.schema.idl.RuntimeWiring;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

/**
 * The read operations of one REST service, as a generator wrote them from the service's
 * documentation into a JSON file next to the schema.
 *
 * @param operations the operations, one for each root field
 * @param mapFields the fields that hold a map in the service's answers, by type. The
 * schema has a list of entries with a key and a value for each of them.
 * @param fields the fields whose name in the schema differs from the key in the service's
 * answers, or whose value is converted, by type. The services answer in Spanish, and the
 * schema names their keys in English.
 */
public record RestOperationTable(List<RestOperation> operations, Map<String, List<String>> mapFields,
		Map<String, Map<String, FieldSource>> fields) {

	public RestOperationTable {
		mapFields = (mapFields != null) ? mapFields : Map.of();
		fields = (fields != null) ? fields : Map.of();
	}

	/**
	 * Where a field of the schema finds its value in an answer of the service.
	 *
	 * @param key the key in the service's answer, such as <code>nombre</code>. Some
	 * services spell a key differently from one operation to the next, such as
	 * <code>idLinea</code> and <code>idlinea</code>, and the key then lists the spellings
	 * separated by <code>|</code>. A key of a nested object is written as a path, such as
	 * <code>count/result</code>.
	 * @param conversion how the value is converted for the schema, or null when it is
	 * taken as it is
	 */
	public record FieldSource(String key, ValueConversion conversion) {

		Object read(Object source) {
			Object value = null;
			for (String spelling : key.split("\\|")) {
				value = (value != null) ? value : readPath(source, spelling);
			}
			return (conversion != null) ? conversion.apply(value) : value;
		}

		private static Object readPath(Object source, String path) {
			Object value = source;
			for (String key : path.split("/")) {
				if (!(value instanceof Map<?, ?> map)) {
					return null;
				}
				value = map.get(key);
			}
			return value;
		}

	}

	/**
	 * Reads the operations of one service from the JSON file that the generator wrote.
	 */
	public static RestOperationTable read(Resource resource, JsonMapper mapper) {
		try (InputStream json = resource.getInputStream()) {
			return mapper.readValue(json, RestOperationTable.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the operations in " + resource, ex);
		}
	}

	/**
	 * Registers a {@link RestOperationFetcher} for each root field, a fetcher that turns
	 * a map into entries for each map field, and a fetcher for each field that reads
	 * another key or converts its value.
	 * @param wiring the runtime wiring of the schema
	 * @param upstream the name of the service, in words a client can read
	 * @param baseUrl the URL the paths of the operations start from
	 * @param client the client that calls the service
	 * @param mapper the mapper that reads the JSON of the answers
	 */
	public void register(RuntimeWiring.Builder wiring, String upstream, String baseUrl, RestClient client,
			JsonMapper mapper) {
		wiring.type("Query", (type) -> {
			operations.forEach((operation) -> type.dataFetcher(operation.field(),
					new RestOperationFetcher(upstream, baseUrl, client, operation, mapper)));
			return type;
		});
		mapFields.forEach((typeName, typeFields) -> wiring.type(typeName, (type) -> {
			typeFields.forEach((field) -> type.dataFetcher(field,
					(environment) -> RestOperationFetcher.entries(((Map<?, ?>) environment.getSource()).get(field))));
			return type;
		}));
		fields.forEach((typeName, typeFields) -> wiring.type(typeName, (type) -> {
			typeFields.forEach(
					(field, source) -> type.dataFetcher(field, (environment) -> source.read(environment.getSource())));
			return type;
		}));
	}

}
