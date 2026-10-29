# Spring Petclinic: GraphQL, then agents

> [!IMPORTANT]
> **This branch is the workshop's starting point.** It is Spring Petclinic with the persistence layer prepared for GraphQL and a React frontend, but without any GraphQL code: we build the API together during the workshop. `starting-point` is the repository's default branch, so cloning the repository puts you where the workshop begins.
>
> Each chapter has a checkpoint branch with the code as it stands at the end of that chapter, [listed below](#branches), so you can catch up or compare at any point. `main` is the original Spring Petclinic, and `git diff main starting-point` shows everything the starting point prepares. The instructions are in the [`workshop/`](workshop/) folder.

A four-hour hands-on workshop at **J On The Beach**, presented by **Željko Kozina**. For two hours we turn Spring Petclinic into a GraphQL API with Spring for GraphQL. For the next two hours we put that API in front of AI agents: as MCP tools generated and curated with GATool, as tools of a chat inside the application, and as a schema an agent explores by itself.

Start with [the setup page](workshop/00-setup.md). Some downloads are large, so do it before the workshop.

## Branches

Each branch holds the code as it stands at the end of one chapter, exercises included, and builds on the branch before it. `git switch <branch>` catches you up, and `git diff <branch>` compares your work with it.

| Branch | Code at the end of |
| --- | --- |
| [`checkpoint-01-graphql-api`](../../tree/checkpoint-01-graphql-api) | [1. Your first GraphQL service](workshop/01-your-first-graphql-service.md) |
| [`checkpoint-02-relationships`](../../tree/checkpoint-02-relationships) | [2. Relationships and the N+1 problem](workshop/02-relationships-and-the-n-plus-1-problem.md) |
| [`checkpoint-03-mutations`](../../tree/checkpoint-03-mutations) | [3. Mutations, input types and a custom scalar](workshop/03-mutations-input-types-and-a-custom-scalar.md) |
| [`checkpoint-04-errors-and-validation`](../../tree/checkpoint-04-errors-and-validation) | [4. Errors and validation](workshop/04-errors-and-validation.md) |
| [`checkpoint-05-security`](../../tree/checkpoint-05-security) | [5. Security](workshop/05-security.md) |
| [`checkpoint-06-interfaces-and-unions`](../../tree/checkpoint-06-interfaces-and-unions) | [6. Interfaces and unions](workshop/06-interfaces-and-unions.md) |
| [`checkpoint-07-subscriptions`](../../tree/checkpoint-07-subscriptions) | [7. Subscriptions](workshop/07-subscriptions.md) |
| [`checkpoint-08-testing`](../../tree/checkpoint-08-testing) | [8. Testing](workshop/08-testing.md) |
| [`checkpoint-09-ready-for-agents`](../../tree/checkpoint-09-ready-for-agents) | [9. Ready for agents: descriptions and query limits](workshop/09-ready-for-agents.md) |
| [`checkpoint-10-generated-mcp-tools`](../../tree/checkpoint-10-generated-mcp-tools) | [10. MCP tools generated from the schema](workshop/10-mcp-tools-generated-from-the-schema.md) |
| [`checkpoint-11-curated-operations`](../../tree/checkpoint-11-curated-operations) | [11. Curated operations and trusted documents](workshop/11-curated-operations-and-trusted-documents.md) |
| [`checkpoint-12-in-app-assistant`](../../tree/checkpoint-12-in-app-assistant) | [12. An assistant inside the application](workshop/12-an-assistant-inside-the-application.md) |
| [`checkpoint-13-mcp-security`](../../tree/checkpoint-13-mcp-security) | [13. Securing the MCP endpoint](workshop/13-securing-the-mcp-endpoint.md) |
| [`checkpoint-14-dynamic-tools`](../../tree/checkpoint-14-dynamic-tools) | [14. Dynamic tools](workshop/14-dynamic-tools.md) |
| [`checkpoint-15-large-schema`](../../tree/checkpoint-15-large-schema) | [15. A large schema built from Spanish open data](workshop/15-a-large-schema-built-from-spanish-open-data.md) |

## What the starting point adds to Spring Petclinic

- **Services for every chapter.** Owner, pet, visit and vet services with paging, filtering, sorting, search and batch lookups, so the workshop writes only GraphQL code.
- **Lazy, two-way relationships.** GraphQL decides what each request reads. A pet knows its owner, and a visit knows its pet and its vet.
- **More data.** The ten owners Spring Petclinic comes with, and five from Málaga and Torremolinos: 15 owners, 22 pets and 32 visits, three of them still to come.
- **A React frontend** in [`frontend/`](frontend/) that lights up page by page as the API grows.
- **The frontend's operations** as `.graphql` files in `src/main/resources/operations`, which the second half hands to an agent as tools.

## Running the backend

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` in place of `./mvnw`.

- The original Petclinic web pages: http://localhost:8080
- GraphiQL, once chapter 1 adds it: http://localhost:8080/graphiql

## Running the frontend

The frontend needs Node.js. Install its packages the first time, then start it:

```bash
cd frontend
npm install    # the first time only
npm run dev
```

Open http://localhost:5173. If you see `sh: vite: command not found`, run `npm install` first.

## Changing the port

If port 8080 is taken, start the backend on another port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

Then tell the frontend where the backend is:

```bash
PETCLINIC_BACKEND=http://localhost:8090 npm run dev
```

In Windows PowerShell, that is `$env:PETCLINIC_BACKEND="http://localhost:8090"; npm run dev`.

## Versions

| Component | Version |
|-----------|---------|
| Java | 21 or newer (tested with 21.0.12 and 25.0.2) |
| Spring Boot | 4.1.0 |
| Maven | 3.9.16, through the Maven wrapper `./mvnw` |
| Node.js | 22 or 24 (tested with 24.10.0) |
| Frontend | React 19.2.4, Vite 8.0.1, graphql-js 16.13.1 |
| Database | H2, in memory |

## Credits

Built on [Spring Petclinic](https://github.com/spring-projects/spring-petclinic) by the Spring team. See [LICENSE.txt](LICENSE.txt).
