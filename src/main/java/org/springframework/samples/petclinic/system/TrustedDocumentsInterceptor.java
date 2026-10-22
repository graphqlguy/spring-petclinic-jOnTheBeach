package org.springframework.samples.petclinic.system;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import graphql.language.AstPrinter;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.stereotype.Component;

@Component
class TrustedDocumentsInterceptor implements WebGraphQlInterceptor {

	// Every operation the frontend and the agent send, by the SHA-256 hash of its file
	private final Map<String, String> documents = new HashMap<>();

	private final boolean trustedOnly;

	TrustedDocumentsInterceptor(@Value("${petclinic.trusted-documents-only}") boolean trustedOnly) throws IOException {
		this.trustedOnly = trustedOnly;
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		for (String location : List.of("classpath:operations/**/*.graphql", "classpath:operations-ui/**/*.graphql")) {
			for (Resource file : resolver.getResources(location)) {
				byte[] content = file.getContentAsByteArray();
				documents.put(sha256(content), printed(new String(content, StandardCharsets.UTF_8)));
			}
		}
	}

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		Object hash = hashOf(request);
		if (hash != null) {
			String document = documents.get(hash);
			if (document == null) {
				return refuse(request, ErrorType.NOT_FOUND,
						"No operation file has this hash. After you change an operation file, restart the backend.");
			}
			// Run our own copy of the file, whatever text came with the hash
			request.configureExecutionInput((input, builder) -> builder.query(document).build());
			return chain.next(request);
		}
		// GATool sends the text of an operation file as GraphQL Java prints it, so its
		// tool calls match one of our documents
		if (trustedOnly && !documents.containsValue(request.getDocument())) {
			return refuse(request, ErrorType.FORBIDDEN,
					"This server runs trusted documents alone: send the SHA-256 hash of an operation file");
		}
		return chain.next(request);
	}

	// The frontend sends the hash in extensions.persistedQuery.sha256Hash, where Apollo's
	// clients send it too
	private static Object hashOf(WebGraphQlRequest request) {
		return (request.getExtensions().get("persistedQuery") instanceof Map<?, ?> persistedQuery)
				? persistedQuery.get("sha256Hash") : null;
	}

	private static Mono<WebGraphQlResponse> refuse(WebGraphQlRequest request, ErrorType type, String message) {
		ExecutionResult result = ExecutionResult.newExecutionResult()
			.addError(GraphqlErrorBuilder.newError().errorType(type).message(message).build())
			.build();
		return Mono
			.just(new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result)));
	}

	// GATool reads @gatool(...) on an operation. The schema does not declare it, so it
	// comes out before the document runs, as the frontend takes it out too.
	private static String printed(String text) {
		Document document = Parser.parse(text);
		List<Definition> definitions = document.getDefinitions()
			.stream()
			.map((definition) -> (definition instanceof OperationDefinition operation) ? withoutGatool(operation)
					: definition)
			.toList();
		return AstPrinter.printAst(document.transform((builder) -> builder.definitions(definitions)));
	}

	private static Definition withoutGatool(OperationDefinition operation) {
		return operation.transform((builder) -> builder.directives(operation.getDirectives()
			.stream()
			.filter((directive) -> !directive.getName().equals("gatool"))
			.toList()));
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
