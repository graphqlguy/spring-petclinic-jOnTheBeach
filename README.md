# Spring Petclinic: GraphQL, then agents

> [!IMPORTANT]
> **This branch is the workshop's starting point.** It is Spring Petclinic with the persistence layer prepared for GraphQL and a React frontend, but without any GraphQL code: we build the API together during the workshop. `starting-point` is the repository's default branch, so cloning the repository puts you where the workshop begins.
>
> Each chapter has a `checkpoint_N` branch with the code as it stands at the end of that chapter, so you can catch up or compare at any point. `main` is the original Spring Petclinic, and `git diff main starting-point` shows everything the starting point prepares. The instructions are in the [`workshop/`](workshop/) folder.

A four-hour hands-on workshop at **J On The Beach**, presented by **Željko Kozina**. For two hours we turn Spring Petclinic into a GraphQL API with Spring for GraphQL. For the next two hours we put that API in front of AI agents: as MCP tools generated and curated with GATool, as tools of a chat inside the application, and as a schema an agent explores by itself.

Start with [the setup page](workshop/00-setup.md). Some downloads are large, so do it before the workshop.

## What the starting point adds to Spring Petclinic

- **Services for every chapter.** Owner, pet, visit and vet services with paging, filtering, sorting, search and batch lookups, so the workshop writes only GraphQL code.
- **Lazy, two-way relationships.** GraphQL decides what each request reads. A pet knows its owner, and a visit knows its pet and its vet.
- **More data.** 40 owners in and around Málaga, 60 pets and 179 visits, ten of them still to come.
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
