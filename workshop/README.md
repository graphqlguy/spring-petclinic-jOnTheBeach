# Workshop guide

Each chapter is one file: numbered steps with the complete code to copy, and most end with exercises whose solutions are folded below them (try first). Start with [the setup page](00-setup.md).

## How it works

The repository you cloned is the **starting point**: Spring Petclinic with its services, its data and a React frontend ready. The GraphQL API is what we add, step by step. After each chapter, the `checkpoint_N` branch holds the code as it should be, exercises included.

The frontend checks the backend's schema at startup and shows only the pages the API can serve. Each chapter lights up another part of it: change the schema, restart the backend, refresh the browser.

## Part 1: GraphQL

1. [Your first GraphQL service](01-your-first-graphql-service.md): the dependency, GraphiQL, your first schema, queries with arguments, paging and filtering, deprecation
2. Relationships and the N+1 problem
3. Mutations, input types and a custom scalar
4. Errors and validation
5. Security
6. Interfaces and unions
7. Subscriptions
8. Testing
9. Ready for agents: descriptions and query limits

## Part 2: agents

10. MCP tools generated from the schema
11. Curated operations, and trusted documents
12. An assistant inside the application
13. Securing the MCP endpoint
14. Dynamic tools: an agent that explores the schema
15. A large schema built from Spanish open data
