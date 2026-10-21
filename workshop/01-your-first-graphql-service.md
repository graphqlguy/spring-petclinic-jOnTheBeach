# Chapter 1: Your first GraphQL service

> **User story** · As the clinic's receptionist, I want to look up owners by last name or city, a page at a time, so that I can find a client while they wait at the desk.

GraphQL lets a client ask for exactly the fields it needs. In this chapter we turn Petclinic into a GraphQL service: we add the dependency, write our first schema, connect it to the services that are already there, and finish with a query that filters, sorts and pages.

By the end of this chapter, you will:

- Have a running GraphQL endpoint with an in-browser IDE (GraphiQL)
- Understand schema-first development: the schema is the contract, and Java implements it
- Write queries with arguments, default values and input types
- Change an API without breaking its clients, using `@deprecated`

## 1. Add the dependency

Spring for GraphQL builds on GraphQL Java and wires it up the Spring way, so you rarely touch the engine directly. One starter brings in everything we need. Add it to the `<dependencies>` section, next to `spring-boot-starter-webmvc`:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

> [!NOTE]
> If you build with Gradle, add `implementation 'org.springframework.boot:spring-boot-starter-graphql'` to `build.gradle` instead.

## 2. Turn on GraphiQL

GraphiQL is an in-browser IDE for GraphQL: a query editor with autocomplete and the API's documentation, served by our own application. It is off by default. Add these two lines below the `# Web` section:

`src/main/resources/application.properties`

```properties
# GraphQL
spring.graphql.graphiql.enabled=true
```

## 3. Write your first schema

This is schema-first development: we describe the API in the GraphQL Schema Definition Language (SDL), and only then implement it. Spring reads every `.graphqls` file under `src/main/resources/graphql/`, and that folder does not exist yet.

**Create a folder named `graphql`** under `src/main/resources`, and in it **a file named `schema.graphqls`**. A schema file in any other place is ignored. Fill it with one query and one type:

`src/main/resources/graphql/schema.graphqls`

```graphql
# The entry point of our API. This line is a comment, by the way:
# comments start with # and are only for people reading this file.
type Query {
    ownersAll: [Owner!]!
}

"""
An owner of one or more pets, and a client of the clinic.
Triple quotes allow a description over several lines.
"""
# We extend the Owner type as the workshop goes on
type Owner {
    id: ID!
    firstName: String!
    lastName: String!
    address: String!
    city: String!
    "Ten digits, such as 9521234567"
    telephone: String!
}
```

| Syntax | Meaning |
|---|---|
| `type Query` | The entry point. Every field of `Query` is a query a client can send. Every schema **must** have a `Query` type with at least one field; mutations and subscriptions are optional. |
| `[Owner!]!` | A list of owners. The outer `!` makes the list itself non-null, and the inner `!` makes each entry non-null. |
| `ID!` | An identifier, sent as a string. The `!` means a value is always there. |
| `String!` | Text that is always there. A field without `!` may be `null`. |

### Descriptions and comments

SDL has two ways to write text, and they serve different readers:

| Syntax | Name | Who sees it |
|---|---|---|
| `"..."` | Description, one line | **Clients.** It is part of the schema: introspection returns it, and GraphiQL and every other GraphQL tool show it. |
| `"""..."""` | Description, several lines | **Clients**, the same as above. |
| `# ...` | Comment | **Only you.** The parser drops it, so it stays in the file. |

Rule of thumb: if a client of your API would benefit from reading it, make it a description. If it is a note for whoever edits the schema file, make it a comment. In the second half of the workshop the client is an AI agent, and it reads the descriptions to decide which field to use.

## 4. Start the server and explore

Start the application:

```bash
./mvnw spring-boot:run
```

Among the startup lines, Spring for GraphQL reports how the schema matches the Java code, and where the endpoint is:

```text
GraphQL schema inspection:
	Unmapped fields: {Query=[ownersAll]}
	Unmapped registrations: {}
	Unmapped arguments: {}
...
GraphQL endpoint HTTP POST /graphql
```

`Unmapped fields` lists the schema fields that Java does not answer yet. `ownersAll` is there, because we write its Java in step 5.

> [!NOTE]
> If startup fails with `Port 8080 was already in use`, another program holds the port. See [changing the port](../README.md#changing-the-port), and use your port in place of `8080` in the addresses below.

Open GraphiQL at [http://localhost:8080/graphiql](http://localhost:8080/graphiql) and click the book icon at the top left to open the documentation explorer. `ownersAll` and the `Owner` type are there, with their descriptions. Nobody wrote this documentation by hand: GraphiQL asked the API for its own schema. This is called **introspection**, and the frontend and the agents use it too.

Look at the two kinds of text: the descriptions of `Owner` and `telephone` are there, and the `#` comments are missing.

Now run this query in the editor, with the ▶ button:

```graphql
query {
  ownersAll {
    id
    lastName
  }
}
```

The answer is an error:

```json
{
  "errors": [
    {
      "message": "The field at path '/ownersAll' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value. ...",
      "path": ["ownersAll"],
      "extensions": { "classification": "NullValueInNonNullableField" }
    }
  ],
  "data": null
}
```

The error is expected: the schema promises a non-null list, and the Java code that would produce one does not exist yet, so GraphQL reports the broken promise. The schema says *what* the API offers, and the implementation comes next.

## 5. Implement the controller

The service layer is ready: `OwnerService` reads the owners from the database. We add the GraphQL entry point, a controller whose methods Spring for GraphQL maps to schema fields.

**Create a new Java class** named `OwnerGraphQlController` in the `org.springframework.samples.petclinic.owner` package, next to `OwnerService` in `src/main/java/org/springframework/samples/petclinic/owner/`. That package already has an `OwnerController`, which serves the Petclinic web pages, so ours gets `GraphQl` in its name.

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQlController.java`

```java
package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class OwnerGraphQlController {

	private final OwnerService ownerService;

	OwnerGraphQlController(OwnerService ownerService) {
		this.ownerService = ownerService;
	}

	@QueryMapping
	List<Owner> ownersAll() {
		return this.ownerService.findAll();
	}

}
```

| Element | Meaning |
|---|---|
| `@Controller` | A Spring bean. Spring for GraphQL looks for handler methods in it. |
| `@QueryMapping` | Binds the method to the `Query` field with the same name, `ownersAll`. |
| The constructor | Spring passes in the `OwnerService` bean. |

Restart the application: stop it with `Ctrl+C`, and run `./mvnw spring-boot:run` again. The startup report now says `Unmapped fields: {}`. Run the query again, and the answer holds all 40 owners.

Remove `id` from the query, or add `firstName` and `city`, and run it again. The answer always has exactly the fields you asked for: in GraphQL, **the client decides the shape of the response**.

## 6. Fetch a single owner

Clients also need one owner at a time. This query takes an argument.

> [!NOTE]
> From here on, the code shows only **what changes**. A `# ...existing queries...` comment stands for what is already in the file and stays as it is. Add the new lines to the existing `Query` type; do not replace the whole block.

Schema first:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    owner(id: ID!): Owner
}
```

The type is `Owner`, without `!`. When no owner has the id, the field is `null`, and the rest of the response is unaffected.

Then Java, in the same controller:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQlController.java`

```java
import org.springframework.graphql.data.method.annotation.Argument;

@QueryMapping
Owner owner(@Argument Integer id) {
	return this.ownerService.findById(id);
}
```

`@Argument` binds the GraphQL argument to the method parameter of the same name, and converts the `ID` to an `Integer` on the way. Restart and try it:

```graphql
query {
  owner(id: 40) {
    firstName
    lastName
    address
    city
  }
}
```

```json
{
  "data": {
    "owner": {
      "firstName": "J On The",
      "lastName": "Beach",
      "address": "Avenida de José Ortega y Gasset 201",
      "city": "Málaga"
    }
  }
}
```

Try `owner(id: 999)` as well. The answer is `{"data": {"owner": null}}`, as the schema allows.

## 7. Evolve the API: filtering, sorting and paging

`ownersAll` returns every owner in one response. That works for 40 owners, and it stops working for a clinic with thousands: the response and the memory it takes grow with every new client. We need paging and filtering. But clients may already depend on `ownersAll`, so we can't simply remove it.

First we mark the old query as deprecated, then we add a better one. Change the `ownersAll` line:

`src/main/resources/graphql/schema.graphqls`

```graphql
    ownersAll: [Owner!]! @deprecated(reason: "Unbounded. Use 'owners' instead.")
```

Add the new query to the `Query` type:

```graphql
type Query {
    # ...existing queries...
    owners(filter: OwnerFilter, page: Int! = 0, size: Int! = 10, sort: OwnerSort): OwnerPage!
}
```

Add the types it uses at the end of the file. These are new types, outside `Query`:

```graphql
enum OwnerSortField {
    LAST_NAME
    FIRST_NAME
    CITY
}

enum SortOrder {
    ASC
    DESC
}

input OwnerFilter {
    "Owners whose last name starts with this text, in upper or lower case"
    lastName: String
    "Owners who live in this city, in upper or lower case"
    city: String
}

input OwnerSort {
    field: OwnerSortField
    order: SortOrder
}

"One page of owners. Pages count from 0."
type OwnerPage {
    content: [Owner!]!
    totalElements: Int!
    totalPages: Int!
    currentPage: Int!
    size: Int!
    isFirst: Boolean!
    isLast: Boolean!
    hasNext: Boolean!
    hasPrevious: Boolean!
}
```

| Syntax | Meaning |
|---|---|
| `enum` | A closed set of values. The engine refuses any other value before your code runs. |
| `input` | A type for arguments. An `input` serves only as an argument, and a `type` only as a result: GraphQL keeps the two directions apart. |
| `page: Int! = 0` | A default value. A client may leave `page` out and gets `0`, but it cannot send `null`. |
| `@deprecated` | The field keeps working, and every tool marks it as deprecated, with the reason. |

The Java side is one method. The `OwnerFilter`, `OwnerSort` and `OwnerPage` records are already in the starting point, and Spring for GraphQL builds the records from the input objects by matching their field names. Add the method to the controller:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQlController.java`

```java
@QueryMapping
OwnerPage owners(@Argument OwnerFilter filter, @Argument int page, @Argument int size, @Argument OwnerSort sort) {
	return this.ownerService.findOwners(filter, page, size, sort);
}
```

Restart, open the documentation explorer again, and look at `ownersAll`: GraphiQL now marks it as deprecated and shows our reason. Existing clients keep working, and everyone reading the schema can see what to move to. Try the new query:

```graphql
query {
  owners(
    filter: { city: "Málaga" }
    sort: { field: FIRST_NAME, order: DESC }
    page: 0
    size: 5
  ) {
    totalElements
    totalPages
    content {
      firstName
      lastName
    }
  }
}
```

23 owners live in Málaga, so there are 5 pages of 5, starting with Sara Delgado. Then try `owners(page: null)`. The engine refuses it during validation, before any Java runs:

```text
Validation error (WrongType@[owners]) : argument 'page' with value 'NullValue{}' must not be null
```

## 8. Start the frontend

Let's look at our API through a real client. The repository has a React frontend that reads our schema when it starts and turns on the pages the API can serve. In a second terminal, from the project folder:

```bash
cd frontend
npm install    # the first time only
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The home page lists what the API can do so far, and **Find owners** is live: filtering by last name and city, sorting and paging, all through the `owners` query you just wrote. Click an owner to open their page.

The frontend sends only the operations in `src/main/resources/operations`. Open `queries/findOwners.graphql`: it is a query like the ones you wrote in GraphiQL, with variables in place of the values. We come back to this folder in chapter 11.

> [!TIP]
> Each time you extend the schema from now on: restart the backend, then refresh the browser. The frontend reads the schema again and turns on whatever is new.

## Exercises

The **Veterinarians** page needs two queries. `VetService` is ready, and so are the `Vet` and `Specialty` entities. You write the schema and the controller, in the same order as above: schema first, then Java.

### Exercise 1: list the vets

Add a `vets` query that returns every vet. A vet has an `id`, a `firstName`, a `lastName` and a list of `specialties`, and a specialty has an `id` and a `name`. Use `VetService.findAll()`, in a new `VetGraphQlController` in the `org.springframework.samples.petclinic.vet` package (`VetController` already serves the web pages). When it works, the frontend shows a **Veterinarians** link.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    vets: [Vet!]!
}

"A veterinarian of the clinic"
type Vet {
    id: ID!
    firstName: String!
    lastName: String!
    specialties: [Specialty!]!
}

"A field a vet specializes in, such as surgery"
type Specialty {
    id: ID!
    name: String!
}
```

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQlController.java`

```java
package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQlController {

	private final VetService vetService;

	VetGraphQlController(VetService vetService) {
		this.vetService = vetService;
	}

	@QueryMapping
	List<Vet> vets() {
		return this.vetService.findAll();
	}

}
```

`specialties` works without Java code: GraphQL calls `Vet.getSpecialties()`, the getter with the field's name. Chapter 2 explains how that works.

</details>

### Exercise 2: fetch a single vet

Add a `vet(id: ID!)` query. `VetService.findById` returns the vet, or throws an `EntityNotFoundException` when no vet has the id. Query `vet(id: 99)` and look at what GraphQL makes of the exception, in the response and in the application's log. Chapter 4 turns it into a clear error. With this query, clicking a vet in the frontend opens their page.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    vet(id: ID!): Vet
}
```

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQlController.java`

```java
import org.springframework.graphql.data.method.annotation.Argument;

@QueryMapping
Vet vet(@Argument Integer id) {
	return this.vetService.findById(id);
}
```

For `vet(id: 99)` the response hides the exception and gives an id to look up in the log:

```json
{
  "errors": [
    {
      "message": "INTERNAL_ERROR for e93e1918-0779-72f3-d7f1-7c70b0d5fe22",
      "path": ["vet"],
      "extensions": { "classification": "INTERNAL_ERROR" }
    }
  ],
  "data": { "vet": null }
}
```

The log has the details: `Unresolved EntityNotFoundException for executionId e93e1918-...`, followed by `Vet with id 99 does not exist`. GraphQL sends an exception it does not know as `INTERNAL_ERROR`, so the details stay in the log.

</details>

## Recap

- One dependency and one property gave us a GraphQL endpoint and GraphiQL.
- The schema is the contract: we wrote SDL first, every time, and implemented it second.
- `@QueryMapping` binds a `Query` field to a controller method, and `@Argument` binds its arguments.
- The client chooses the fields, and the response has exactly those.
- An API grows in place: `@deprecated` and a better query, without a `/v2`.

Chapter 2 connects owners to their pets, pets to their visits, and visits to the vets who saw them.
