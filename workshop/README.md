# Workshop guide

Each chapter is one file: numbered steps with the complete code to copy, and most end with exercises whose solutions are folded below them (try first). Start with [the setup page](00-setup.md).

## How it works

The repository you cloned is the **starting point**: Spring Petclinic with its services, its data and a React frontend ready. The GraphQL API is what we add, step by step. After each chapter, the `checkpoint_N` branch holds the code as it should be, exercises included.

The frontend checks the backend's schema at startup and shows only the pages the API can serve. Each chapter lights up another part of it: change the schema, restart the backend, refresh the browser.

## Part 1: GraphQL

1. [Your first GraphQL service](01-your-first-graphql-service.md): the dependency, GraphiQL, your first schema, queries with arguments, paging and filtering, deprecation
2. [Relationships and the N+1 problem](02-relationships-and-the-n-plus-1-problem.md): nested fields, default field resolution, `@SchemaMapping`, the N+1 problem and `@BatchMapping`
3. [Mutations, input types and a custom scalar](03-mutations-input-types-and-a-custom-scalar.md): a `Date` scalar, the `Mutation` type, input types, variables and partial updates
4. [Errors and validation](04-errors-and-validation.md): error classifications, `@GraphQlExceptionHandler`, partial responses and Bean Validation on arguments
5. [Security](05-security.md): a resource server, a `login` mutation that signs a token, `@PreAuthorize` on mutations and on a single field
6. [Interfaces and unions](06-interfaces-and-unions.md): a search across types, inline fragments, `__typename`, and when to use which
7. [Subscriptions](07-subscriptions.md): the `Subscription` type, WebSocket and Server-Sent Events, a Reactor sink fed by application events
8. [Testing](08-testing.md): `HttpGraphQlTester`, errors and tokens in tests, a subscription test, the `@GraphQlTest` slice
9. Ready for agents: descriptions and query limits

## Part 2: agents

10. MCP tools generated from the schema
11. Curated operations, and trusted documents
12. An assistant inside the application
13. Securing the MCP endpoint
14. Dynamic tools: an agent that explores the schema
15. A large schema built from Spanish open data
