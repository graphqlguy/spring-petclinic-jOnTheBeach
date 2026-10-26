# Workshop guide

Each chapter is one file: numbered steps with the complete code to copy, and most end with exercises whose solutions are folded below them (try first). Start with [the setup page](00-setup.md).

## How it works

The repository you cloned is the **starting point**: Spring Petclinic with its services, its data and a React frontend ready. The GraphQL API is what we add, step by step. After each chapter, the `checkpoint_N` branch holds the code as it should be, exercises included.

The frontend checks the backend's schema at startup and shows only the pages the API can serve. Each chapter lights up another part of it: change the schema, restart the backend, refresh the browser.

## Part 1: GraphQL

1. [Your first GraphQL service](01-your-first-graphql-service.md): the dependency, GraphiQL, your first schema, queries with arguments, paging and filtering, deprecation
2. [Relationships and the N+1 problem](02-relationships-and-the-n-plus-1-problem.md): nested fields, default field resolution, `@SchemaMapping`, the N+1 problem and `@BatchMapping`
3. [Mutations, input types and a custom scalar](03-mutations-input-types-and-a-custom-scalar.md): a `Date` scalar, the `Mutation` type, input types, variables and partial updates
4. [Errors and validation](04-errors-and-validation.md): error classifications, `@GraphQlExceptionHandler`, partial responses and validation directives on arguments
5. [Security](05-security.md): a resource server, a `login` mutation that signs a token, `@PreAuthorize` on mutations and on a single field
6. [Interfaces and unions](06-interfaces-and-unions.md): a search across types, inline fragments, `__typename`, and when to use which
7. [Subscriptions](07-subscriptions.md): the `Subscription` type, WebSocket and Server-Sent Events, a Reactor sink fed by application events
8. [Testing](08-testing.md): `HttpGraphQlTester`, errors and tokens in tests, a subscription test, the `@GraphQlTest` slice
9. [Ready for agents: descriptions and query limits](09-ready-for-agents.md): descriptions a caller can act on, introspection, depth and complexity limits

## Part 2: agents

10. [MCP tools generated from the schema](10-mcp-tools-generated-from-the-schema.md): GATool, the MCP Inspector, generated tools and their selection depth, a typed `Date` scalar, a write tool with a staff token
11. [Curated operations and trusted documents](11-curated-operations-and-trusted-documents.md): the frontend's operation files as tools, partial answers counted as results, an interceptor that runs operations by their hash and can refuse every other document
12. [An assistant inside the application](12-an-assistant-inside-the-application.md): Spring AI's `ChatClient` with the operation files as in-process tools, tool calls that act as the signed-in user, checking what the model did, errors a model can act on, hosted models, conversation memory
13. [Securing the MCP endpoint](13-securing-the-mcp-endpoint.md): how an MCP client signs in, Spring Security's authorization server inside the application, open client registration, tokens bound to the endpoint, the MCP Inspector signed in as staff and as guest
14. [Dynamic tools](14-dynamic-tools.md): `searchSchema`, `introspectType` and `executeGraphql`, a system prompt that says when to write a query, a field that does the counting, the checks on a query the model wrote, a schema search by meaning with an embedding model
15. [A large schema built from Spanish open data](15-a-large-schema-built-from-spanish-open-data.md): 16 public services in 602 root fields and 150,000 tokens, a hand-written HTTP client, a SOAP client and generated operation tables, recorded answers for the tests and the conference wifi, and an API that turns the failures of services and of a small model into errors and types the model can act on
