# Chapter 14: Dynamic tools

> **User story** · As the clinic manager, I want to ask the assistant questions outside its tools, such as how many visits each vet has had, so that I get the answer without waiting for a new release.

The operation files answer the questions the frontend asks. GATool can publish three more tools, with which the model writes its own query: it searches the schema for the fields it needs, reads their types, and runs the query. In this chapter, the MCP clients and the assistant get those three tools, and we look at what a model does with them.

By the end of this chapter, you will:

- Publish three tools with which a model writes its own GraphQL
- Follow the model's three steps in the MCP Inspector
- Add a field that answers what the model cannot work out
- Know which checks apply to a query that the model wrote
- Search the schema by meaning, with an embedding model

## 1. The three tools

| Tool | Takes | Returns |
|---|---|---|
| `searchSchema` | A question in plain words | The fields that match it best, each with its declaration and the root field that reaches it |
| `introspectType` | The name of a type | The type's fields, with their arguments and descriptions |
| `executeGraphql` | A query and its variables | The result, as an operation file's tool returns it |

The search ranks **schema coordinates**: a coordinate names one field of one type, such as `Vet.visits`. One search returns at most 2000 tokens, however large the schema is, so the model reads only the part of the schema it needs. Chapter 15 relies on that, with a schema too large to read whole.

## 2. Add the search index

`searchSchema` ranks the coordinates with Lucene, which GATool's starters leave out. Add it next to the Ollama starter:

`pom.xml`

```xml
<dependency>
  <groupId>org.apache.lucene</groupId>
  <artifactId>lucene-core</artifactId>
  <version>10.3.2</version>
</dependency>
<dependency>
  <groupId>org.apache.lucene</groupId>
  <artifactId>lucene-analysis-common</artifactId>
  <version>10.3.2</version>
</dependency>
```

> [!NOTE]
> With Gradle, add `implementation 'org.apache.lucene:lucene-core:10.3.2'` and `implementation 'org.apache.lucene:lucene-analysis-common:10.3.2'` to `build.gradle`.

## 3. Publish the tools

Add a Dynamic tools section below the MCP section:

`src/main/resources/application.properties`

```properties
# Dynamic tools
# Three more tools, with which the model writes its own GraphQL: searchSchema, introspectType
# and executeGraphql. MCP clients and the assistant both get them, after the operation files.
gatool.dev.experimental.generate-tools=dynamic-three-step
```

Restart. After the list of tools, the log shows:

```text
WARN ... o.a.l.i.v.VectorizationProvider          : Java vector incubator module is not readable. For optimal vector performance, pass '--add-modules jdk.incubator.vector' to enable Vector API.
WARN ... i.g.b.a.GAToolAutoConfiguration          : gatool.dev.experimental.generate-tools is dynamic-three-step, so a model can search your schema and run GraphQL it wrote against your API. The trusted documents keep working beside it. Turn this off before you deploy.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 48 schema coordinates with BM25 and returning up to 2000 tokens of results per call.
```

| Line | What it says |
|---|---|
| Lucene's warning | Lucene can use a JDK module that speeds up large indexes. Ours holds 48 coordinates, so the default works. |
| GATool's warning | A model can now run queries it wrote itself. Step 7 shows the checks that still apply. |
| GATool's count | The search ranks 48 coordinates with BM25, the word statistics that Lucene computes. The mutations, the subscription and the deprecated `ownersAll` are left out. |

## 4. Follow the three steps in the Inspector

Reload the Inspector's page and flip the switch on the card. It reconnects with its token from chapter 13. If you stopped the Inspector, start it with the command of chapter 13 and sign in. The **Tools** tab lists 15 tools: the 12 operation files, then the three new ones.

Run `searchSchema` with:

```json
{"question": "visits of each vet"}
```

```text
Pet.visits:
reach it from Query.search(text: String!) -> ... on Pet
"""The visits of this pet, oldest first"""
visits: [Visit!]!  # on type Pet

Vet.visits:
reach it from Query.vets
"""The visits this vet has seen or will see, newest first"""
visits: [Visit!]!  # on type Vet

Query.vet:
"""One vet with their visits. An id that no vet has returns a NOT_FOUND error. vets and search give the ids of vets."""
vet(id: ID!): Vet  # on type Query
...
```

The answer holds 19 coordinates, best first. `Vet.visits` comes with the root field that reaches it, `Query.vets`. Run `introspectType` with:

```json
{"name": "Vet"}
```

```graphql
"""A veterinarian of the clinic"""
type Vet implements Person {
  id: ID!
  firstName: String!
  lastName: String!
  specialties: [Specialty!]!
  """The visits this vet has seen or will see, newest first"""
  visits: [Visit!]!
}
```

Run `executeGraphql` with:

```json
{"document": "{ vets { firstName lastName visits { id } } }"}
```

```json
{"data":{"vets":[{"firstName":"James","lastName":"Carter","visits":[{"id":"178"},{"id":"177"},{"id":"172"},...
```

These are the calls a model makes: the search names the fields and the root field that reaches them, and the type gives the names to select.

## 5. Tell the assistant when to write a query

The assistant gets the three tools too. Asked how many visits each vet has had, `granite4.1:8b` started with `listVets` and `findVisits` in all 4 of our runs, and every answer had wrong counts. Two lines in the system prompt say when to write a query. Replace the whole `ChatController`:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java`

```java
package org.springframework.samples.petclinic.chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;
import jakarta.servlet.http.HttpSession;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools, ChatMemory memory) {
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider())
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
			.build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question, HttpSession session) {
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read and change the clinic's data.
				Changing data needs a signed-in staff member, so when a tool answers Unauthorized, ask the user to sign in.
				When a question is about many owners, pets, vets or visits at once, write one query that answers it:
				find the fields with searchSchema, read their types with introspectType, and run the query with executeGraphql.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
		return this.chatClient.prompt()
			.system(system)
			.user(question)
			.advisors((advisor) -> advisor.param(ChatMemory.CONVERSATION_ID, session.getId()))
			.call()
			.content();
	}

	// The chat panel shows this text, for example when Ollama is not running
	@ExceptionHandler
	ResponseEntity<String> modelFailed(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The model did not answer: " + ex.getMessage());
	}

}
```

This is chapter 12's class with the conversation memory of its exercise, and the two new lines in the system prompt. Restart, open the assistant in the frontend, and ask:

```text
How many visits has each vet had? Give their names.
```

The log shows the model's three steps:

```text
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: searchSchema
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: executeGraphql
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: executeGraphql
```

The model wrote a query such as `{ vets { id visits { id } } }`, which returns every visit of every vet, and then counted the ids. These are the counts it gave in our five runs:

| Vet | Visits | Run 1 | Run 2 | Run 3 | Run 4 | Run 5 |
|---|---|---|---|---|---|---|
| James Carter | 15 | 15 | 15 | 15 | 9 | 12 |
| Helen Leary | 2 | 2 | 2 | 2 | 2 | 2 |
| Linda Douglas | 7 | 6 | 6 | 6 | 6 | 6 |
| Rafael Ortega | 4 | 4 | 4 | 4 | 4 | 4 |
| Henry Stevens | 0 | 0 | 0 | 0 | 0 | 0 |
| Sharon Jenkins | 4 | 4 | 4 | 4 | 4 | 4 |

The query was right in every run, and every answer had at least one count wrong. A model reads text in tokens, and counting the ids in a JSON answer is a task it gets wrong, even for a list of 7.

## 6. A field that does the counting

A field that counts the visits of a vet moves the arithmetic into the API. Replace the `Vet` type:

`src/main/resources/graphql/schema.graphqls`

```graphql
"A veterinarian of the clinic"
type Vet implements Person {
    id: ID!
    firstName: String!
    lastName: String!
    specialties: [Specialty!]!
    "The visits this vet has seen or will see, newest first"
    visits: [Visit!]!
    "The number of visits this vet has seen or will see"
    visitCount: Int!
}
```

In `VetGraphQlController`, add this method below the `visits` method:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQlController.java`

```java
	@BatchMapping
	Map<Vet, Integer> visitCount(List<Vet> vets) {
		Map<Vet, List<Visit>> visits = visits(vets);
		return vets.stream().collect(Collectors.toMap((vet) -> vet, (vet) -> visits.get(vet).size()));
	}
```

`visitCount` calls the batch method of chapter 2, which reads the visits of every vet in one SQL statement, and returns the size of each list. Restart: the log says `ranking 49 schema coordinates`. Ask the assistant the same question. The log shows `searchSchema` and `executeGraphql`, with a query such as:

```graphql
{ vets { id firstName lastName visitCount } }
```

The counts were right in all 5 of our runs, each answered within 6 seconds. The tools and the operation files stayed as they were: the search found `Vet.visitCount` because its description says what it holds. A field that does the arithmetic gives every agent the right number, and the description is how the search finds it. The two lines of step 5 are still needed: without them, the model kept to `listVets` and `findVisits`, and missed the new field in all 4 of our runs.

## 7. What a query from the model can do

`executeGraphql` runs a query the model wrote, so it passes through every check of the API, and GATool adds checks of its own. Run `executeGraphql` in the Inspector with each of these:

| Document | Answer |
|---|---|
| `mutation { addVisit(input: {petId: 17, description: "x-ray"}) { id } }` | A **Tool Error**: *This server runs queries here, and that document is a mutation. gatool.dev.experimental.dynamic-operations.allow-mutations turns writing on, and it is an unsafe switch because the operation is one nobody reviewed.* |
| `{ __schema { types { name } } }` | A **Tool Error**: *That document selects __schema, and introspection runs through the schema tools here: search the schema for what you need, and read one type in full by its name.* |
| `{ vets { visits { pet { owner { pets { visits { vet { visits { pet { name } } } } } } } } } }` | A **Tool Error**: `maximum query depth exceeded 10 > 8`, from chapter 9's depth limit |
| `{ owner(id: 12) { firstName lastName telephone } }` | Signed in as guest: `"telephone": null`, with a `FORBIDDEN` error from chapter 5's `@PreAuthorize`. Signed in as staff: `"telephone": "9521471193"`. |

| Check | Where it runs |
|---|---|
| Queries only | GATool, before the API. Writes go through the operation files, which someone reviewed. |
| Introspection | GATool, which sends the model to `searchSchema` and `introspectType`, within their token budget |
| Size | GATool allows 15 levels, 500 fields and 30 aliases. Chapter 9's limits of 8 levels and a complexity of 200 are stricter, and they apply to every query. |
| The caller's rights | Our resolvers, with the caller's token, as for the operation files |

## 8. Search by meaning

BM25 ranks a coordinate by the words it shares with the question. In the Inspector, run `searchSchema` with each of these:

| Question | The first coordinate |
|---|---|
| `phone number` | `Vet.visitCount`, whose description holds *number* |
| `doctors` | *Nothing in this schema matched "doctors".* |
| `¿Cuántas visitas ha tenido cada veterinario?` | `OwnerPage.hasNext`, then `OwnerPage.hasPrevious`, then `Vet.visits` |

An **embedding model** turns a text into a vector of numbers, and two texts with similar meanings get vectors that point the same way. The setup pulled one tuned on GraphQL schemas. Add two lines to the Dynamic tools section:

`src/main/resources/application.properties`

```properties
# searchSchema ranks the schema's fields by meaning, with the embedding model pulled in the setup
gatool.dev.experimental.dynamic-operations.search-backend=embedding
spring.ai.ollama.embedding.model=qwen3-graphql-embedder
```

Restart:

```text
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool is embedding 49 schema coordinates, which is one call to your embedding model for each of them. The result is cached, so later starts read it.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool cached them in /var/folders/.../T/gatool-<you>/vectors/<hash>.vectors.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 49 schema coordinates with EMBEDDING and returning up to 2000 tokens of results per call.
```

Lucene's warning is gone, because the embedding search runs without Lucene. The next start says `GATool read 49 embedded schema coordinates from its cache`. Run the three searches again:

| Question | The first coordinate |
|---|---|
| `phone number` | `Owner.telephone` |
| `doctors` | `Specialty.vets`, then `Query.vets` |
| `¿Cuántas visitas ha tenido cada veterinario?` | `Vet.visitCount`, then `Vet.visits` |

Ask the assistant the Spanish question, `¿Cuántas visitas ha tenido cada veterinario? Dame sus nombres.` It answered with the right counts in all 4 of our runs with the embedding search, and in 2 of 4 runs with BM25. In some runs, the model translated the question into English before it searched.

> [!NOTE]
> The embedding model runs in Ollama. If you use a hosted model from chapter 12, add `gatool.dev.experimental.dynamic-operations.search-backend=bm25` to its `application-<name>.properties`. With the Mistral or OpenAI starter, the application holds two embedding models, and GATool stops at startup and asks which one to use.

> [!NOTE]
> The tests start the application, which now calls Ollama at startup when its cache is empty. To keep the tests independent of Ollama, create `src/test/resources/config/application.properties`. Spring Boot reads it after the main file, so its value wins in the tests:
>
> ```properties
> # The tests run without Ollama, so searchSchema ranks the schema's fields by their words.
> gatool.dev.experimental.dynamic-operations.search-backend=bm25
> ```

## Exercise: trusted documents only

Set `petclinic.trusted-documents-only=true`, restart, and ask the assistant how many visits each vet has had. Then make it answer correctly while the property stays `true`.

In this mode, chapter 11's interceptor refuses the document that `executeGraphql` sends:

```json
{"errors":[{"message":"This server runs trusted documents alone: send the SHA-256 hash of an operation file","locations":[],"extensions":{"classification":"FORBIDDEN"}}]}
```

The model then used `listVets` and `findVisits`, and in our 3 runs it answered 0 visits for every vet twice, and counted the visits of October once.

<details>
<summary>Show solution</summary>

The model wrote the query in step 6. Save it as an operation file, with a description that says when to use it:

`src/main/resources/operations/queries/countVisitsByVet.graphql`

```graphql
# Lists every vet with the number of visits they have seen or will see.
# Use it for questions such as how many visits each vet has had, or which vet is the busiest.
query CountVisitsByVet {
  vets {
    id
    firstName
    lastName
    visitCount
  }
}
```

Restart. The log says `GATool serves 13 MCP tools` and lists `countVisitsByVet`. Ask again: the log shows one tool call, `countVisitsByVet`, and the counts were right in all 3 of our runs, within 3 seconds. The interceptor trusts the file, as it trusts every file in `operations/`, and every agent finds the tool in the tool list.

Set `petclinic.trusted-documents-only` back to `false`: chapter 15 runs on `executeGraphql`.

</details>

## Recap

- `generate-tools=dynamic-three-step` publishes `searchSchema`, `introspectType` and `executeGraphql`, with which a model finds the fields it needs and writes its own query.
- The system prompt tells the model when to write a query, since a model with operation files at hand prefers them.
- A small model counts badly, so a field that does the arithmetic gives it the right answer, and its description lets the search find it.
- A query from the model runs only as a read, without introspection, within GATool's and our size limits, and with the caller's rights.
- An embedding model ranks the schema by meaning, so a question in other words, or in Spanish, still finds the field.
- In trusted-documents-only mode, a query the model found becomes an operation file, which every agent reads as a tool.

Chapter 15 builds a large schema from Spanish open data, and the assistant explores it with the three tools.
