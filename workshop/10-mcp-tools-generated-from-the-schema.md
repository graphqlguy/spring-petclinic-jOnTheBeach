# Chapter 10: MCP tools generated from the schema

> **User story** · As the clinic manager, I want our API to work with the AI assistants the staff already use, so that they can look up owners and book visits by asking in their own words.

The Model Context Protocol (MCP) is an open standard for connecting AI applications to the systems they work with. A server publishes **tools**, and the AI application hands them to its model, which picks a tool and its arguments. In this chapter, [GATool](https://github.com/api-agent-bridge/gatool) publishes our GraphQL API as MCP tools, one for every query and mutation, written from the schema.

By the end of this chapter, you will:

- Know how an MCP server, its tools and an AI application fit together
- Serve the GraphQL API as MCP tools with GATool
- List and call the tools with the MCP Inspector
- Choose what the generated tools select, and give the `Date` scalar a type for them
- Call a write tool with a staff token

## 1. MCP in brief

| Term | Meaning |
|---|---|
| MCP server | Publishes tools at an endpoint. Ours will be `http://localhost:8080/mcp`. |
| Tool | A name, a description and a JSON Schema for its arguments. The model reads all three to choose a tool. |
| MCP client | The part of an AI application that connects to a server, lists its tools, and calls the one the model picks. The MCP Inspector is a client for trying a server by hand. |
| `tools/list`, `tools/call` | The two requests this chapter uses. They are JSON-RPC messages, sent with HTTP POST to the endpoint (the **Streamable HTTP** transport). |

GATool turns each GraphQL operation into one tool. The tool's arguments are the operation's variables, its description comes from the schema descriptions of chapter 9, and a call runs the operation inside our application.

## 2. Add GATool

Add the starter next to the security starter:

`pom.xml`

```xml
<dependency>
  <groupId>io.gatool</groupId>
  <artifactId>gatool-mcp-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

GATool needs Java 21 or newer, which is why the workshop asks for it.

> [!NOTE]
> With Gradle, add `implementation 'io.gatool:gatool-mcp-spring-boot-starter:0.1.0'` to `build.gradle`.

Add an MCP section below the `# Security` section:

`src/main/resources/application.properties`

```properties
# MCP
# GATool serves the GraphQL API as MCP tools at /mcp, one tool for each query and mutation
gatool.dev.experimental.generate-tools=all_root_queries_and_mutations
# Chapter 13 secures the endpoint. Until then, an MCP client connects without signing in.
gatool.mcp.security.unsafe.allow-mcp-calls-without-authentication=true
```

| Property | Meaning |
|---|---|
| `generate-tools` | Writes one GraphQL operation for each field of `Query` and `Mutation`, and publishes each operation as a tool. It is a development setting, under `dev.experimental`, because it hands the whole API to a model. Chapter 11 publishes operations we write ourselves. `Subscription` stays out, since a tool call returns one answer. |
| `allow-mcp-calls-without-authentication` | GATool secures the endpoint by default and stops at startup until it knows which server issues the tokens: `Invalid value 'null' for configuration property 'spring.security.oauth2.resourceserver.jwt.issuer-uri'`. Chapter 13 sets that up. Until then, this property opens the endpoint. |

## 3. Restart and read the log

Restart. GATool says how it runs the tools, and lists them:

```text
GATool runs each document inside this application.
GATool serves 15 MCP tools:
  ownersAll from generated:query/ownersAll (about 82 tokens)
  owner from generated:query/owner (about 98 tokens)
  owners from generated:query/owners (about 330 tokens)
  vets from generated:query/vets (about 70 tokens)
  vet from generated:query/vet (about 101 tokens)
  visits from generated:query/visits (about 183 tokens)
  petTypes from generated:query/petTypes (about 84 tokens)
  search from generated:query/search (about 101 tokens)
  people from generated:query/people (about 87 tokens)
  addVisit from generated:mutation/addVisit (about 278 tokens)
  addOwner from generated:mutation/addOwner (about 172 tokens)
  updateOwner from generated:mutation/updateOwner (about 210 tokens)
  addPet from generated:mutation/addPet (about 254 tokens)
  updatePet from generated:mutation/updatePet (about 204 tokens)
  login from generated:mutation/login (about 114 tokens)
  about 2368 tokens in all, which every agent that lists these tools reads before it calls one
```

| Line | Meaning |
|---|---|
| `runs each document inside this application` | A tool call goes straight to the GraphQL engine, in the same application, without an HTTP request to `/graphql`. |
| `about 82 tokens` | The size of the tool's name, description and JSON Schema. An AI application sends every tool to its model with every question, so these tokens are paid on every request. |
| `login` | Every root field became a tool, `login` too: a tool that takes a password and returns a token the agent cannot use, since a token travels in a header that the MCP client sets. Chapter 11 publishes only the operations we choose. |

The log goes on to list the same tools as **in-process** tools, which chapter 12 gives to an assistant inside the application. It also warns that the MCP endpoint answers anyone who can reach your laptop. `/graphql` has been open the same way since chapter 1, and the mutations still need a staff token, as step 7 shows.

## 4. Call the tools from the MCP Inspector

In a new terminal, start the MCP Inspector with our server's address. If MCPJam is running too, first set the Inspector's port variables from [step 6 of the setup](00-setup.md#6-fetch-the-mcp-inspector-and-mcpjam):

```bash
npx -y @modelcontextprotocol/inspector@2.9.0 --web --transport http --server-url http://localhost:8080/mcp
```

The Inspector opens in your browser, with one server card, `localhost:8080`.

1. Flip the switch on the card. It says **Connected**, and the **Messages** panel on the right lists the requests the Inspector sent, `INITIALIZE` and `TOOLS/LIST` among them.
2. Open the **Tools** tab at the top. It lists the 15 tools.
3. Select `owners`. The description and the description of each argument are the ones we wrote in chapter 9. The badges **READ-ONLY** and **IDEMPOTENT** come from GATool, which marks every query that way and every mutation **DESTRUCTIVE**.
4. Type `Torremolinos` in **city** and click **Execute Tool**.

```json
{
  "data": {
    "owners": {
      "totalElements": 3,
      "totalPages": 1,
      "currentPage": 0,
      "size": 10,
      "isFirst": true,
      "isLast": true,
      "hasNext": false,
      "hasPrevious": false
    }
  }
}
```

Three owners live in Torremolinos, and the answer leaves out who they are. This is the operation GATool wrote for the tool:

```graphql
query owners($filter: OwnerFilter, $page: Int! = 0, $size: Int! = 10, $sort: OwnerSort) {
  owners(filter: $filter, page: $page, size: $size, sort: $sort) {
    totalElements
    totalPages
    currentPage
    size
    isFirst
    isLast
    hasNext
    hasPrevious
  }
}
```

Every argument of the field became a variable, and the selection holds the scalar fields of `OwnerPage`, one level deep. `content` is a list of objects, a level further down, so it stayed out.

## 5. Choose what the generated tools select

Two more properties shape the generated operations. Add them below `generate-tools`:

`src/main/resources/application.properties`

```properties
# Each tool selects two levels of fields: owners returns a page with the owners on it
gatool.dev.experimental.generated-selection-depth=2
# ownersAll is deprecated, so it gets no tool
gatool.dev.experimental.generate-tools-for-deprecated-root-fields=false
```

Restart. The log says `GATool serves 14 MCP tools`: `ownersAll` is gone, because chapter 1 deprecated it. Reload the Inspector's page, flip the switch again, and run `owners` for Torremolinos. The Inspector shows the answer as a **Tool Error**:

```json
{
  "data": {
    "owners": {
      "content": [
        { "id": "15", "firstName": "J On The", "lastName": "Beach", "address": "Calle México 3", "city": "Torremolinos", "telephone": null },
        { "id": "13", "firstName": "Pablo", "lastName": "Morales", "address": "Calle San Miguel 8", "city": "Torremolinos", "telephone": null },
        { "id": "14", "firstName": "Marta", "lastName": "Ramos", "address": "Avenida Palma de Mallorca 17", "city": "Torremolinos", "telephone": null }
      ],
      "totalElements": 3,
      ...
    }
  },
  "errors": [
    { "message": "Unauthorized", "path": ["owners", "content", 0, "telephone"], "extensions": { "classification": "UNAUTHORIZED" } },
    { "message": "Unauthorized", "path": ["owners", "content", 1, "telephone"], "extensions": { "classification": "UNAUTHORIZED" } },
    { "message": "Unauthorized", "path": ["owners", "content", 2, "telephone"], "extensions": { "classification": "UNAUTHORIZED" } }
  ]
}
```

The owners are there now. The generated selection takes every scalar field of `Owner`, `telephone` included, and chapter 5 keeps the telephone for staff. GATool reports an answer with errors as a failed call, so every anonymous call to `owners` now fails, with the owners inside it.

The depth applies to every tool at once. Here is the size of three answers, in characters, at each depth:

| Tool | Depth 1 | Depth 2 | Depth 3 |
|---|---|---|---|
| `owners` (first page) | 146 | 2,856 | 3,618 |
| `vet` (id 1) | 67 | 1,047 | 1,918 |
| `vets` | 329 | 2,630 | 4,487 |

Depth 2 gives `owners` its owners, and gives `vets` every visit of every vet: 2,630 characters that the model reads to answer a question such as "which vets do surgery?". Each tool needs a selection of its own, which an operation file gives it in chapter 11.

## 6. Give the Date scalar a type

The Inspector marks `visits` with a warning sign. GATool's log says why:

```text
The schema declares custom scalars that GATool describes to the model as any JSON value: Date (used by visits, addVisit, addPet, updatePet).
```

The JSON Schema of the `from` argument holds only a description, so the model may send any JSON value:

```json
"from": {
  "description": "The first day, written as 2026-10-21 An RFC-3339 compliant Full Date Scalar See https://tools.ietf.org/html/rfc3339 for the format of this value."
}
```

GATool reads a scalar's `@specifiedBy` URL, and it knows the specifications published in the GraphQL scalars registry at [scalars.graphql.org](https://scalars.graphql.org). The registry's [LocalDate](https://scalars.graphql.org/andimarek/local-date) describes the format of our `Date`: `YYYY-MM-DD`, such as `1983-10-20`. Point the scalar at it:

`src/main/resources/graphql/schema.graphqls`

```graphql
scalar Date @specifiedBy(url: "https://scalars.graphql.org/andimarek/local-date")
```

Restart, reload the Inspector and connect. The warning sign is gone, and so is the warning in the log. The argument now has a type and a format, which a model and a client can check before they call:

```json
"from": {
  "type": "string",
  "format": "date",
  "description": "The first day, written as 2026-10-21 An RFC-3339 compliant Full Date Scalar"
}
```

To read the schemas yourself, click the arrow ↕ on the `TOOLS/LIST` entry in the **Messages** panel, and find `visits` in the response.

## 7. Write with a staff token

Select `addVisit`. Switch on **Edit as JSON**, replace the arguments with these, and click **Execute Tool**:

```json
{
  "input": {
    "petId": 15,
    "vetId": 3,
    "description": "limping on a back leg"
  }
}
```

```text
Tool Error
{"data":null,"errors":[{"message":"Unauthorized","locations":[{"line":2,"column":3}],"path":["addVisit"],"extensions":{"classification":"UNAUTHORIZED"}}]}
```

The rule from chapter 5 refused the anonymous caller. GATool runs the operation inside the application, with the caller's security context, so `@PreAuthorize` on our controller decides for MCP callers exactly as it does for GraphQL ones.

To call as staff, the Inspector sends a token with every request. In GraphiQL, sign in:

```graphql
mutation {
  login(username: "staff", password: "staff")
}
```

Copy the token. Stop the Inspector with `Ctrl+C`, and start it again with the token in an `Authorization` header:

```bash
npx -y @modelcontextprotocol/inspector@2.9.0 --web --transport http --server-url http://localhost:8080/mcp --header "Authorization: Bearer <paste the token here>"
```

Open the frontend in another tab: since chapter 7, it shows a notification for each booking. In the Inspector, connect and run `addVisit` with the same arguments:

```json
{
  "data": {
    "addVisit": {
      "id": "33",
      "date": "2026-10-23",
      "description": "limping on a back leg",
      "pet": { "id": "15", "name": "Nala", "birthDate": "2023-09-30" },
      "vet": { "id": "3", "firstName": "Linda", "lastName": "Douglas" }
    }
  }
}
```

The date is tomorrow's, because the input left it out. Switch to the frontend's tab, and a notification shows the visit: the tool ran the same `addVisit` mutation, and `VisitService` announced the booking as it does for the frontend. Chapter 13 replaces the pasted token with a sign-in that the MCP client does by itself.

> [!TIP]
> Any AI assistant that speaks MCP over HTTP can use these tools. With Claude Code, for example, add the server with `claude mcp add --transport http petclinic http://localhost:8080/mcp`.

## Exercise: book a visit the way an agent does

A receptionist hears names, and the tools need ids. Pablo Morales from Torremolinos calls: his cat needs an x-ray tomorrow, with a vet who does radiology. Book it in the Inspector with the tools alone, and note which calls you needed. An agent makes the same calls, guided by the descriptions of chapter 9.

<details>
<summary>Show solution</summary>

1. `search` with `{"text": "Morales"}` returns the owner with id `13`, and at depth 2 his pets: Perla, with id `17`.
2. `vets` returns each vet with their specialties. Helen Leary (id `2`) and Henry Stevens (id `5`) do radiology.
3. `addVisit`, with the staff token, books it. The date stays out, since the description says a visit without a date is booked for tomorrow:

```json
{
  "input": {
    "petId": 17,
    "vetId": 2,
    "description": "x-ray"
  }
}
```

Three calls, and the answer to the second one carries every visit of every vet. A tool written for this question would return the vets and their specialties alone.

</details>

## Recap

- MCP lets an AI application discover tools on a server and call the one its model picks.
- GATool publishes GraphQL operations as MCP tools. With `generate-tools`, it writes one operation per root field.
- The tool's description and argument descriptions come from the schema; a query is marked read-only, a mutation destructive.
- A tool call runs inside the application, so `@PreAuthorize` decides for MCP callers too.
- One selection depth fits some tools and overloads others, and `@specifiedBy` gives a custom scalar a type that tools can publish.

Chapter 11 replaces the generated tools with operations we write: each selects what its question needs.
