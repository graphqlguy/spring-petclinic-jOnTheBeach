# Chapter 11: Curated operations and trusted documents

> **User story** · As the clinic manager, I want the assistant to use the same reviewed operations as our frontend, so that each answer holds what its question needs, and the API runs only the operations we reviewed.

GATool wrote chapter 10's tools from the schema, and they showed three problems. The frontend already has an operation file for each of its pages, written for one purpose and described for its reader. In this chapter, GATool publishes those files as tools, and the server learns to run those files alone, which the GraphQL documentation calls **trusted documents**.

By the end of this chapter, you will:

- Publish operation files as MCP tools
- Know what an operation file decides about its tool
- Count an answer with a missing field as an answer
- Accept an operation by its hash, and refuse every operation outside the files

## 1. What the generated tools got wrong

| Chapter 10 | What happened | This chapter |
|---|---|---|
| Every owner selection took `telephone` | Every anonymous call to `owner`, `owners` and `search` failed | Each file selects what its question needs |
| One selection depth for every tool | `vets` returned 2,630 characters to say which vet does surgery | `listVets` selects the names and the specialties |
| Every root field became a tool | `login` asked the model for a password | The files for the agent leave `login` out |

## 2. The frontend's operations

The frontend sends only the operations in two folders, one `.graphql` file each:

| Folder | Holds |
|---|---|
| `src/main/resources/operations` | The queries and mutations of the pages: 7 in `queries/` and 5 in `mutations/` |
| `src/main/resources/operations-ui` | What the frontend alone needs: `login`, the schema check, the two summaries of chapter 1, the owner's pets and the vet's visits of chapter 2, and the `visitBooked` subscription |

Open one of them:

`src/main/resources/operations/queries/findOwners.graphql`

```graphql
# Finds owners by the start of their last name, by their city, or both, and returns one page of them with their id and address.
# The last name and the city match in upper or lower case, and leaving both out lists every owner.
# Pages count from 0. To answer a question about all owners, read every page.
# getOwnerDetails gives the telephone, the pets and the visits of one owner.
query FindOwners($lastName: String, $city: String, $page: Int = 0, $size: Int = 10, $sortBy: OwnerSortField = LAST_NAME, $order: SortOrder = ASC) {
  owners(filter: {lastName: $lastName, city: $city}, page: $page, size: $size, sort: {field: $sortBy, order: $order}) {
    totalElements
    totalPages
    currentPage
    hasNext
    hasPrevious
    content {
      id
      firstName
      lastName
      address
      city
    }
  }
}
```

The `#` lines say what the operation is for and which operation to call next, as chapter 9 advised. The selection leaves `telephone` out, since the list of owners does not show it.

## 3. Publish the files as tools

In the MCP section, replace the three generator lines and their comments with these two lines. The unsafe switch below them stays until chapter 13:

`src/main/resources/application.properties`

```properties
# GATool serves the frontend's operations as MCP tools at /mcp, one tool for each file
gatool.mcp.operations.locations=classpath*:operations/
```

The three generator lines are gone, and without them `generate-tools` is off, its default. `classpath*:operations/` names a folder: GATool reads every `.graphql` file in it and in its subfolders. `operations-ui` stays out, and with it `login`.

Restart. GATool checks every file against the schema, then lists the tools:

```text
GATool validates every operation file against the schema this application serves.
GATool serves 11 MCP tools:
  addOwner from file:.../target/classes/operations/mutations/addOwner.graphql (about 176 tokens)
  addPet from file:.../target/classes/operations/mutations/addPet.graphql (about 255 tokens)
  addVisit from file:.../target/classes/operations/mutations/addVisit.graphql (about 301 tokens)
  updateOwner from file:.../target/classes/operations/mutations/updateOwner.graphql (about 185 tokens)
  updatePet from file:.../target/classes/operations/mutations/updatePet.graphql (about 193 tokens)
  findOwners from file:.../target/classes/operations/queries/findOwners.graphql (about 334 tokens)
  getOwnerDetails from file:.../target/classes/operations/queries/getOwnerDetails.graphql (about 127 tokens)
  getVetDetails from file:.../target/classes/operations/queries/getVetDetails.graphql (about 124 tokens)
  listPetTypes from file:.../target/classes/operations/queries/listPetTypes.graphql (about 89 tokens)
  listVets from file:.../target/classes/operations/queries/listVets.graphql (about 102 tokens)
  search from file:.../target/classes/operations/queries/search.graphql (about 123 tokens)
  about 2009 tokens in all, which every agent that lists these tools reads before it calls one
```

A file that asks for a field the schema lacks stops the application at startup, and the message names the file and the line:

```text
GATool read the operation files and found 1 problem:
    file:.../target/classes/operations/queries/listPetTypes.graphql fails validation against the schema:
  - Validation error (FieldUndefined@[petTypes/nickname]) : Field 'nickname' in type 'PetType' is undefined (line 6, column 5)
```

The in-process tools of chapter 10 are gone from the log as well. They read a folder of their own, which chapter 12 sets.

## 4. Call the curated tools

Reload the Inspector's page and flip the switch on the card. If you stopped the Inspector, start it with the first command of chapter 10. Open the **Tools** tab: it lists the 11 tools. Select `findOwners`:

- The description is the four `#` lines of the file.
- The arguments are the variables, with their defaults. `sortBy` and `order` are lists to choose from, filled with the values of their enums.
- `lastName` reads *Owners whose last name starts with this text*: the variable fills `OwnerFilter.lastName`, and GATool takes that field's description from the schema.

Type `Torremolinos` in **city** and click **Execute Tool**:

```json
{
  "data": {
    "owners": {
      "totalElements": 3,
      "totalPages": 1,
      "currentPage": 0,
      "hasNext": false,
      "hasPrevious": false,
      "content": [
        { "id": "15", "firstName": "J On The", "lastName": "Beach", "address": "Calle México 3", "city": "Torremolinos" },
        { "id": "13", "firstName": "Pablo", "lastName": "Morales", "address": "Calle San Miguel 8", "city": "Torremolinos" },
        { "id": "14", "firstName": "Marta", "lastName": "Ramos", "address": "Avenida Palma de Mallorca 17", "city": "Torremolinos" }
      ]
    }
  }
}
```

The owners arrive without a Tool Error, because the file leaves the telephone out. Each part of a tool comes from the file:

| Part of the tool | Comes from |
|---|---|
| Name | The operation's name in camel case: `FindOwners` becomes `findOwners` |
| Description | The `#` lines right above the operation |
| Arguments | The variables, with their types and defaults. Each one's description is the schema's description of the argument or input field the variable fills. |
| Answer | The selection |
| **READ-ONLY**, **IDEMPOTENT**, **DESTRUCTIVE** | The operation type, as in chapter 10 |

Run `listVets`: every vet with their specialties, in 573 characters. Chapter 10's `vets` answered the same question in 2,630.

## 5. Count a partial answer as an answer

Run `getOwnerDetails` with `13` as the **id**. The Inspector shows a **Tool Error**:

```json
{
  "data": {
    "owner": {
      "id": "13",
      "firstName": "Pablo",
      "lastName": "Morales",
      "address": "Calle San Miguel 8",
      "city": "Torremolinos",
      "telephone": null,
      "pets": [ { "id": "17", "name": "Perla", ... } ]
    }
  },
  "errors": [
    { "message": "Unauthorized", "path": ["owner", "telephone"], "extensions": { "classification": "UNAUTHORIZED" } }
  ]
}
```

The frontend's owner page sends the same file, gets the same answer, and shows *visible to staff* in place of the telephone. Everything else is there, and the error says which field is missing and why. GATool reports every answer with errors as a failed call, so that a model notices when part of a call went wrong. In our API, an error next to data marks a field that stays `null` on purpose, so tell GATool. Add a line below `operations.locations`:

`src/main/resources/application.properties`

```properties
# An answer with data and errors is a success: the errors say which fields stay null, and why
gatool.results.partial-results-as-success=true
```

Restart, reload the Inspector and connect, and run `getOwnerDetails` again. The same answer arrives as a result. An answer without data is still a Tool Error: an anonymous `addVisit` returns `"data": null` and the `UNAUTHORIZED` error of chapter 10.

## 6. Trusted documents

Every operation our clients send is now a file in one of the two folders. The frontend sends each one with the SHA-256 hash of its file. Open the frontend's **Vets** page with your browser's developer tools on the **Network** tab, and look at the payload of the request to `graphql`:

```json
{
  "query": "query ListVets {\n  vets {\n    id\n    firstName\n    lastName\n    specialties {\n      id\n      name\n    }\n  }\n}",
  "operationName": "ListVets",
  "variables": {},
  "extensions": {
    "persistedQuery": {
      "version": 1,
      "sha256Hash": "d98d297f92fb2f864b0466e9c73c46d26ca0642aa8853e591d513d4763c4e27b"
    }
  }
}
```

When the server keeps the same hashes, a client can send the hash in place of the text, and the server can refuse every document outside its files. A client then cannot send an operation we have not reviewed, such as the deep query of chapter 9. The GraphQL documentation calls this [trusted documents](https://graphql.org/learn/security/#trusted-documents).

**Create a new class** named `TrustedDocumentsInterceptor` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/TrustedDocumentsInterceptor.java`

```java
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
```

| Element | Meaning |
|---|---|
| `WebGraphQlInterceptor` | Spring for GraphQL passes every request through each interceptor bean before it runs: over HTTP, over a WebSocket, and from GATool. |
| The constructor | Reads every file in both folders, and keeps its document under the SHA-256 hash of the file's bytes, which is what the frontend hashes. |
| A known hash | Runs our copy of the document. The text that came with the hash does not matter. |
| An unknown hash | Refused with `NOT_FOUND`. It happens when you change a file and the backend still runs with the old one. |
| `trustedOnly` | When `true`, a request without a hash runs only when its text is one of our documents, and is refused with `FORBIDDEN` otherwise. |
| `printed(...)` | GATool sends a file as GraphQL Java prints it, so the interceptor keeps that printed form. On the way, it takes out `@gatool(...)`, a directive that gives GATool settings for one tool, such as a title. |

Add the switch to the GraphQL section:

`src/main/resources/application.properties`

```properties
# true runs the files in operations/ and operations-ui/ alone, and refuses every other document
petclinic.trusted-documents-only=false
```

Restart. The hash that the frontend sent is the SHA-256 of the file's bytes. On macOS, Linux or Git Bash, compute it from the project folder:

```bash
shasum -a 256 src/main/resources/operations/queries/listVets.graphql
```

```text
d98d297f92fb2f864b0466e9c73c46d26ca0642aa8853e591d513d4763c4e27b  src/main/resources/operations/queries/listVets.graphql
```

The same bytes always give the same hash, so every copy of the project has this one until someone edits the file. Git on Windows converts line endings by default, which would change the bytes, so the project's `.gitattributes` gives the operation files the same line endings on every system. Ask for the vets with the hash alone:

```bash
curl -s http://localhost:8080/graphql -H 'Content-Type: application/json' \
  -d '{"extensions":{"persistedQuery":{"version":1,"sha256Hash":"d98d297f92fb2f864b0466e9c73c46d26ca0642aa8853e591d513d4763c4e27b"}}}'
```

```json
{"data":{"vets":[{"id":"1","firstName":"James","lastName":"Carter","specialties":[]},{"id":"2","firstName":"Helen","lastName":"Leary","specialties":[{"id":"1","name":"radiology"}]},...]}}
```

> [!NOTE]
> If your system lacks `shasum`, use `sha256sum` in its place. Both print the hash, then the file name.

Now turn the switch on: set `petclinic.trusted-documents-only=true` and restart.

1. In GraphiQL, run `{ petTypes { name } }`:

   ```json
   {
     "errors": [
       {
         "message": "This server runs trusted documents alone: send the SHA-256 hash of an operation file",
         "locations": [],
         "extensions": { "classification": "FORBIDDEN" }
       }
     ]
   }
   ```

2. Reload the frontend and open its pages. Every page works, because every request names the hash of a file.
3. In the Inspector, reload, connect and run `listVets`. It works too: the tool sends the text of `listVets.graphql`, which is one of our documents. A tool that chapter 10's generator wrote would be refused here, since the generator wrote its operation and the text differs from every file.

Our frontend sends the text along with the hash, so that it also works with the servers of chapters 1 to 10. Set the switch back to `false` and restart, so that GraphiQL accepts your own queries in the chapters that follow.

## Exercise: one call less

In chapter 10's exercise, Pablo Morales asked for an x-ray for his cat, with a vet who does radiology. With the curated tools, the booking takes four calls: `search` for his id, `getOwnerDetails` for his pet's id, `listVets` for the vet's id, and `addVisit`. Change `search.graphql` so that the booking takes three, and say so in its description. Then book the x-ray in the Inspector, with the staff token of chapter 10.

<details>
<summary>Show solution</summary>

`search` returns owners, so it can return each owner's pets too. Add `pets` to the `Owner` fragment, and add *each owner with their pets* to the first line of the description:

`src/main/resources/operations/queries/search.graphql`

```graphql
# Searches owners, vets and pets by part of a name, in upper or lower case, and returns every match, each owner with their pets.
# Each result is an Owner, a Vet or a Pet, as __typename says. getOwnerDetails and getVetDetails take the id of an owner or a vet.
query Search($text: String!) {
  search(text: $text) {
    __typename
    ... on Person {
      id
      firstName
      lastName
    }
    ... on Owner {
      city
      pets {
        id
        name
        type {
          name
        }
      }
    }
    ... on Pet {
      id
      name
      type {
        name
      }
      owner {
        id
        firstName
        lastName
      }
    }
  }
}
```

Restart, so that the tool and the server's hash both follow the new file. The frontend reads the new file when you reload it, and its search page shows the same results as before, since it ignores the pets.

1. `search` with `{"text": "Morales"}` returns Pablo Morales with his cat Perla, id `17`.
2. `listVets` shows that Helen Leary (id `2`) and Henry Stevens (id `5`) do radiology.
3. `addVisit`, with the staff token, books it:

```json
{
  "petId": 17,
  "vetId": 2,
  "description": "x-ray"
}
```

The arguments are the file's variables, so they arrive one by one, without the `input` object of chapter 10's generated tool.

</details>

## Recap

- `gatool.mcp.operations.locations` publishes every operation file in a folder as a tool, and GATool checks each file against the schema at startup.
- The file decides the tool: its name, its description from the `#` lines, its arguments from the variables, and its answer from the selection.
- `partial-results-as-success` lets an answer with data and errors count as a success, for an API whose errors mark fields left out on purpose.
- With trusted documents, a client names an operation by the hash of its file, and the server can refuse every other document.
- The agent's tools and the frontend use the same files, so a change to a file changes both, after a restart.

Chapter 12 gives these tools to an assistant inside the application, in a chat on the frontend.
