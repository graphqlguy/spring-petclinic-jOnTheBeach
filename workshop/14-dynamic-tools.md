# Chapter 14: Dynamic tools

> **User story** · As the clinic manager, I want to ask the assistant any question about the clinic's data, such as whether the clinic has a surgeon, so that I get the answer without waiting for a new operation file.

Each operation file answers a question we planned for, and every new question needs a new file and a new release. With three other tools from GATool, the model writes its own query: it searches the schema for the fields it needs, reads a type when it needs more details, and runs the query. In this chapter, MCP clients and the assistant get those three tools alone, on the clinic's small schema, and chapter 15 gives them a large one.

By the end of this chapter, you will:

- Know what an embedding is, and why the search uses one
- Serve the three tools to MCP clients and to the assistant
- Follow the model's three steps in the MCP Inspector
- Know which checks apply to a query that the model wrote

## 1. The three tools

| Tool | Takes | Returns |
|---|---|---|
| `searchSchema` | A question in plain words | The fields that match it best, each with its declaration and the root field that reaches it |
| `introspectType` | The name of a type | The type's fields, with their arguments and descriptions |
| `executeGraphql` | A query and its variables | The result, as an operation file's tool returns it |

The search ranks **schema coordinates**: a coordinate names one field of one type, such as `Vet.visits`. One search returns at most 2000 tokens, however large the schema is, so the model reads only the part of the schema it needs. Chapter 15 relies on that, with a schema too large to read whole.

Here the three tools replace the operation tools, so you can follow the model's steps on their own. `executeGraphql` runs queries only, so from this chapter on, MCP clients and the assistant can only read the clinic's data.

## 2. Search by meaning

A question often uses other words than the schema: a user asks for doctors, and the schema says vet. An **embedding model** turns a text into a vector, a list of numbers, and texts with a similar meaning get vectors that point the same way.

Ollama serves an HTTP API on port 11434 for the models it runs. It runs on its own, apart from our application: on macOS and Windows the Ollama app starts it, and on Linux the installer sets it up as a service. Spring AI calls its `/api/embed` endpoint for every vector GATool needs: the schema's fields at startup, and each question. You can send the same request for one word with `curl`, on macOS, Linux or Git Bash. PowerShell handles the quotes differently, so you might need to adjust the command there.

```bash
curl -s http://localhost:11434/api/embed -d '{"model": "qwen3-graphql-embedder", "input": "doctors"}'
```

```json
{"model":"qwen3-graphql-embedder","embeddings":[[0.015182828,-0.02370876,-0.0013484629,0.09673668,...
```

The vector holds 1,024 numbers. Two vectors are compared by the angle between them, the **cosine similarity**: 1 means the same direction, and 0 means the texts are unrelated. We compared `doctors` with the first sentence of three descriptions in our schema:

| Text | Describes | Similarity to `doctors` |
|---|---|---|
| Every vet of the clinic, with their specialties | `Query.vets` | 0.57 |
| The kinds of pet the clinic treats, sorted by name | `Query.petTypes` | 0.31 |
| The visits from one date to another, both included, in date order, past and booked | `Query.visits` | 0.11 |

`searchSchema` works the same way. At startup, GATool asks the embedding model for the vector of every query field, written as a declaration with its description, and caches the vectors on disk. For each question, it ranks the fields by their similarity to the question's vector and returns the best ones within 2000 tokens.

The model the setup pulled is Qwen3-Embedding-0.6B, fine-tuned on 4,788 pairs of a question and the field that answers it, because a schema is camelCase names and type signatures, while most embedding models learn from prose. Its model card gives the right field among the first 50 results for 63.6% of the 816 questions of the GraphQL AI Working Group's benchmark, against 59.8% for OpenAI's `text-embedding-3-large` and 58.5% for the model before the fine-tune.

## 3. Serve the three tools

In `application.properties`, point both operation locations at a folder that does not exist. The `optional:` prefix lets Spring accept a missing folder, so GATool reads zero operation files and leaves the files where they are. Replace the first two lines below `# MCP`:

`src/main/resources/application.properties` (excerpt)

```properties
# GATool serves the three dynamic tools below alone at /mcp: this folder does not exist, so GATool
# skips the operation files. Chapter 11's trusted documents still read them from operations/.
gatool.mcp.operations.locations=optional:classpath*:none/
```

Add a Dynamic tools section below the MCP section:

```properties
# Dynamic tools
# Three tools, with which the model writes its own GraphQL: searchSchema, introspectType
# and executeGraphql. MCP clients and the assistant both get them.
gatool.dev.experimental.generate-tools=dynamic-three-step
# searchSchema ranks the schema's fields by meaning, with the embedding model pulled in the setup
gatool.dev.experimental.dynamic-operations.search-backend=embedding
spring.ai.ollama.embedding.model=qwen3-graphql-embedder
```

`spring.ai.ollama.embedding.model` names the model that Spring AI asks Ollama for, the name that `ollama cp` gave it in the setup. Ollama loads the model into memory at the first request that names it, and unloads it after 5 minutes without requests: `ollama ps` lists the models it holds.

Replace the first two lines below `# Chat`:

```properties
# The assistant in the frontend gets the same three tools, inside the application
gatool.in-process.operations.locations=optional:classpath*:none/
```

Restart:

```text
WARN ... i.g.b.a.GAToolAutoConfiguration          : GATool serves zero MCP tools. These locations were read: optional:classpath*:none/. An operation file there becomes a tool, and gatool.mcp.operations.locations names the folders.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool is embedding 48 schema coordinates, which is one call to your embedding model for each of them. The result is cached, so later starts read it.
INFO ... i.g.b.i.search.EmbeddingSchemaSearch     : GATool cached them in /var/folders/.../T/gatool-<you>/vectors/<hash>.vectors.
WARN ... i.g.b.a.GAToolAutoConfiguration          : gatool.dev.experimental.generate-tools is dynamic-three-step, so a model can search your schema and run GraphQL it wrote against your API. The trusted documents keep working beside it. Turn this off before you deploy.
INFO ... i.g.b.a.GAToolAutoConfiguration          : GATool publishes searchSchema, introspectType, executeGraphql, ranking 48 schema coordinates with EMBEDDING and returning up to 2000 tokens of results per call.
```

GATool warns that it serves zero MCP tools, which is what the empty folder is for, and that a model can now run queries it wrote itself: step 6 shows the checks that still apply. The 48 coordinates are the schema's query fields, without the mutations, the subscription and the deprecated `ownersAll`. On our laptop, embedding them took under a second, and the next start reads them from the cache.

> [!NOTE]
> The embedding model runs in Ollama. With a hosted model from chapter 12, add `gatool.dev.experimental.dynamic-operations.search-backend=bm25` to its `application-<name>.properties`: BM25 ranks the fields by their words, and runs without a model. With the Mistral or OpenAI starter, the application holds two embedding models, and GATool stops at startup and asks which one to use. The tests run without Ollama, so `src/test/resources/config/application.properties` sets `bm25` for them.

## 4. Follow the three steps in the Inspector

Reload the Inspector's page and flip the switch on the card. It reconnects with its token from chapter 13. If you stopped the Inspector, start it with the command of chapter 13 and sign in. The **Tools** tab lists three tools.

Run `searchSchema` with:

```json
{"question": "Does the clinic have a surgeon?"}
```

```text
Query.vets:
"""Every vet of the clinic, with their specialties"""
vets: [Vet!]!  # on type Query

Specialty.vets:
reach it from Query.vets -> Vet.specialties
"""The vets who have this specialty"""
vets: [Vet!]!  # on type Specialty
...
```

The answer lists all 48 coordinates, best first, because the whole schema fits in 2000 tokens. A field below the root comes with the path that reaches it, such as `Query.vets -> Vet.specialties`. `Vet.specialties` and `Specialty.name` come fourth and fifth, so this result already holds every field the query needs. To see a whole type at once, read it with `introspectType`:

```json
{"name": "Specialty"}
```

```graphql
"""A field a vet specializes in, such as surgery"""
type Specialty {
  id: ID!
  name: String!
  """The vets who have this specialty"""
  vets: [Vet!]!
}
```

Run `executeGraphql` with:

```json
{"document": "{ vets { firstName lastName specialties { name } } }"}
```

```json
{"data":{"vets":[{"firstName":"James","lastName":"Carter","specialties":[]},{"firstName":"Helen","lastName":"Leary","specialties":[{"name":"radiology"}]},{"firstName":"Linda","lastName":"Douglas","specialties":[{"name":"dentistry"},{"name":"surgery"}]},...
```

These are the calls a model makes, and it needs `introspectType` only when the search result leaves out a field it wants. Run `searchSchema` with these three questions to see the search by meaning. The last column shows what the same searches return with BM25, the search by words that the tests use:

| Question | By meaning | By words (BM25) |
|---|---|---|
| `doctors` | `Specialty.vets`, then `Query.vets` | *Nothing in this schema matched "doctors".* |
| `phone number` | `Owner.telephone` | *Nothing in this schema matched "phone number".* |
| `¿Cuántas visitas ha tenido cada veterinario?` | `Vet.visits`, then `Visit.vet` | `OwnerPage.hasNext`, then `OwnerPage.hasPrevious`, then `Vet.visits` |

## 5. Ask the assistant

The assistant can only read now, so the first two lines of chapter 12's system prompt change: the first says how to use the three tools, and the second names the error a guest gets for an owner's telephone. Change the start of the system prompt:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java` (excerpt)

```java
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read the clinic's data:
				find the fields with searchSchema, use introspectType if you need more details about a type,
				and run the query with executeGraphql.
				Some fields, such as an owner's telephone, need a signed-in staff member, so when a tool answers
				Forbidden, ask the user to sign in.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
```

Restart, open the assistant in the frontend, and ask:

```text
Does the clinic have a surgeon?
```

The log shows the model's three steps:

```text
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: searchSchema
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: introspectType
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: executeGraphql
```

In our 5 runs with `granite4.1:8b`, every answer said yes and named Linda Douglas and Rafael Ortega, within 4 seconds. Every answer also mentioned "others" or "another vet" with the surgery specialty, which the data does not hold. Asked for Lucía Navarro's telephone as a guest, the model asked us to sign in in 2 of our 3 runs.

Now ask:

```text
What kinds of pets does the clinic treat?
```

In all 5 of our runs, the model called `searchSchema` and `introspectType`, and answered with cats and dogs, guessed from the type `PetType`, before it ran a query. The clinic treats birds, cats, dogs, hamsters, lizards and snakes. Two stronger system prompts kept the same two calls in 10 more runs. The operation file `listPetTypes` answers this question in one call.

A real application can serve both kinds of tools: the operation files to every caller, and the three tools to signed-in staff. One property makes the three tools need the scope that chapter 13 gives staff:

```properties
gatool.mcp.operations.locations=classpath*:operations/
# The three tools need the scope that chapter 13 gives staff, on top of mcp:tools
gatool.dev.experimental.dynamic-operations.required-scopes=petclinic:write
```

A guest still sees the 15 tools, and a call to `searchSchema` gets `403` with `error="insufficient_scope"`: *searchSchema needs mcp:tools petclinic:write. The token holds mcp:tools and lacks petclinic:write.* We skip this setup in the workshop for time.

## 6. What a query from the model can do

`executeGraphql` runs a query the model wrote, so it passes through every check of the API, and GATool adds checks of its own. These are documents we sent with `executeGraphql` from the Inspector, and the answers they got:

| Document | Answer |
|---|---|
| `mutation { addVisit(input: {petId: 17, description: "x-ray"}) { id } }` | A **Tool Error**: *This server runs queries here, and that document is a mutation. gatool.dev.experimental.dynamic-operations.allow-mutations turns writing on, and it is an unsafe switch because the operation is one nobody reviewed.* |
| `{ __schema { types { name } } }` | A **Tool Error**: *That document selects __schema, and introspection runs through the schema tools here: search the schema for what you need, and read one type in full by its name. __typename inside a selection is fine.* |
| `{ vets { visits { pet { owner { pets { visits { vet { visits { pet { name } } } } } } } } } }` | A **Tool Error**: `maximum query depth exceeded 10 > 5`, from chapter 9's depth limit |
| `{ owner(id: 12) { firstName lastName telephone } }` | Signed in as guest: `"telephone": null`, with a `FORBIDDEN` error from chapter 5's `@PreAuthorize`. Signed in as staff: `"telephone": "9521471193"`. |

GATool refuses mutations and introspection before the API sees the document. GATool's own size limits are 15 levels, 500 fields and 30 aliases, so chapter 9's stricter limits decide, and our resolvers check the caller's rights as they do for the operation files.

## Exercise: count the visits

Ask the assistant:

```text
How many visits has each vet had? Give their names.
```

The schema holds each vet's visits as a list, so the model reads every visit and counts them. In our 5 runs, every answer had at least one count wrong: Linda Douglas has 7 visits, and every answer said 6. A model reads text in tokens, and it miscounts the ids in a JSON answer, even in a list of 7. Make the counts right.

<details>
<summary>Show solution</summary>

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

In `VetGraphQLController`, add this method below the `visits` method:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
	@BatchMapping
	Map<Vet, Integer> visitCount(List<Vet> vets) {
		Map<Vet, List<Visit>> visits = visits(vets);
		return vets.stream().collect(Collectors.toMap((vet) -> vet, (vet) -> visits.get(vet).size()));
	}
```

`visitCount` calls the batch method of chapter 2, which reads the visits of every vet in one SQL statement, and returns the size of each list. Restart: the schema changed, so GATool embeds it again, and the log says `GATool is embedding 49 schema coordinates`. For the question `How many visits has each vet had?`, `searchSchema` now ranks `Vet.visitCount` first, because its description says what it holds. Ask the assistant again: the counts were right in all 5 of our runs, each within 4 seconds. A field that does the arithmetic gives every agent the right number.

</details>

## Recap

- `generate-tools=dynamic-three-step` publishes `searchSchema`, `introspectType` and `executeGraphql`, with which a model writes its own query, and an operation location that does not exist serves them alone.
- An embedding model turns a text into a vector, and the search ranks the fields by how close their vectors are to the question's, so `doctors` finds the vets.
- A small model can answer before it runs a query, where an operation file answers in one call. `required-scopes` serves both kinds of tools, with the three for staff alone.
- A query from the model runs only as a read, without introspection, within the size limits, and with the caller's rights.

Chapter 15 builds a large schema from Spanish open data, and the assistant explores it with the same three tools.
