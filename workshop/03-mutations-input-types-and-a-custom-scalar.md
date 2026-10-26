# Chapter 3: Mutations, input types and a custom scalar

> **User story** · As the receptionist, I want to book visits, register new clients and their pets, and correct their details, so that the front desk works from one screen.

So far the API only reads. In this chapter it starts to change data. First we give dates a type of their own, because the new operations take dates as input. Then we add the `Mutation` type, input types, variables, and an update that changes only the fields a client sends.

By the end of this chapter, you will:

- Add a custom scalar and use it in results and in input
- Write mutations with `@MutationMapping`
- Pass structured input with `input` types, and values with variables
- Write a partial update: a mutation that changes the fields the client sends, and keeps the others as they are

## 1. A type for dates

`birthDate` and `date` are strings at the moment. A client cannot tell a date from a name, and the API would accept any text where a date belongs. GraphQL has five built-in scalars, `Int`, `Float`, `String`, `Boolean` and `ID`, and lets a schema add its own. The GraphQL Java project publishes common ones, among them a `Date`. Add the library next to the GraphQL starter:

`pom.xml`

```xml
<dependency>
  <groupId>com.graphql-java</groupId>
  <artifactId>graphql-java-extended-scalars</artifactId>
  <version>24.0</version>
</dependency>
```

> [!NOTE]
> With Gradle, add `implementation 'com.graphql-java:graphql-java-extended-scalars:24.0'` to `build.gradle`.

A scalar needs Java code that turns values into text and back, and the library provides it. We register it with the engine. **Create a new class** named `GraphQLScalarsConfiguration` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLScalarsConfiguration.java`

```java
package org.springframework.samples.petclinic.system;

import graphql.scalars.ExtendedScalars;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration(proxyBeanMethods = false)
class GraphQLScalarsConfiguration {

	@Bean
	RuntimeWiringConfigurer scalars() {
		return (wiring) -> wiring.scalar(ExtendedScalars.Date);
	}

}
```

Declare the scalar on the first line of the schema, above the comment from chapter 1, and use it for the two dates:

`src/main/resources/graphql/schema.graphqls`

```graphql
scalar Date @specifiedBy(url: "https://tools.ietf.org/html/rfc3339")
```

```graphql
type Pet {
    # ...existing fields...
    birthDate: Date
}

type Visit {
    # ...existing fields...
    date: Date!
}
```

Change the existing `birthDate` and `date` lines; do not add second ones.

GraphQL accepts the definitions of a schema in any order, so a field may use a type that is declared further down. We declare scalars first, so that a reader meets `Date` before the fields that use it.

| Element | Meaning |
|---|---|
| `scalar Date` | Declares a type whose values are single pieces of text or numbers, with rules the server applies. |
| `RuntimeWiringConfigurer` | Hands the engine the Java code behind the scalar. The schema declares it, the wiring implements it. |
| `@specifiedBy` | Points clients to the scalar's specification, here RFC 3339 dates such as `2026-10-21`. |

Restart and run the query from chapter 2 again. The dates look the same, `"2023-09-30"`, but the documentation explorer now shows them as `Date`, described as *An RFC-3339 compliant Full Date Scalar*. In Java, they arrive and leave as `LocalDate`.

## 2. Your first mutation: book a visit

Queries read, and **mutations** change data. A schema declares them in a second root type, `Mutation`. Booking a visit takes four values, so we group them in an **input type**. Add both to the schema:

`src/main/resources/graphql/schema.graphqls`

```graphql
"Changes to the clinic's data"
type Mutation {
    addVisit(input: AddVisitInput!): Visit!
}

input AddVisitInput {
    petId: ID!
    "Leave it out to book the visit without a vet"
    vetId: ID
    "Leave it out to book the visit for tomorrow"
    date: Date
    description: String!
}
```

The Java side is one method. The starting point has a matching `AddVisitInput` record, and Spring for GraphQL fills it in by matching the field names. **Create a new class** named `VisitGraphQLController` in the `org.springframework.samples.petclinic.owner.visit` package:

`src/main/java/org/springframework/samples/petclinic/owner/visit/VisitGraphQLController.java`

```java
package org.springframework.samples.petclinic.owner.visit;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class VisitGraphQLController {

	private final VisitService visitService;

	VisitGraphQLController(VisitService visitService) {
		this.visitService = visitService;
	}

	@MutationMapping
	Visit addVisit(@Argument AddVisitInput input) {
		return visitService.addVisit(input);
	}

}
```

Restart and book a visit for Woofy, the conference dog (pet 21), with James Carter (vet 1):

```graphql
mutation {
  addVisit(
    input: { petId: 21, vetId: 1, date: "2026-10-30", description: "check-up after the conference" }
  ) {
    id
    date
    pet {
      name
    }
    vet {
      lastName
    }
  }
}
```

The answer is the new visit, with id 33: the seed data has 32. A mutation returns an object like any query, so the client asks for exactly the fields it needs next. When an operation has several mutation fields, GraphQL runs them one after another, in order, where it may run the fields of a query in parallel.

`date` is a `Date`, so the scalar also checks what clients send. Run the mutation again with the date written the way many people do, `date: "30/10/2026"`. The engine refuses it during validation, before our code runs, so the clinic's visits stay as they were:

```text
Validation error (WrongType@[addVisit]) : argument 'input.date' with value 'StringValue{value='30/10/2026'}' is not a valid 'Date' - Invalid RFC3339 full date value : '30/10/2026'. ...
```

## 3. Variables: register an owner

So far every value sat inside the query text. Real clients keep the operation fixed and send the values separately, as **variables**. Add the mutation and its input:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Mutation {
    # ...existing mutations...
    addOwner(input: AddOwnerInput!): Owner!
}

input AddOwnerInput {
    firstName: String!
    lastName: String!
    address: String!
    city: String!
    "Ten digits, such as 9521234567"
    telephone: String!
}
```

Add the method to `OwnerGraphQLController`:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import org.springframework.graphql.data.method.annotation.MutationMapping;

@MutationMapping
Owner addOwner(@Argument AddOwnerInput input) {
	return ownerService.addOwner(input);
}
```

Restart. In GraphiQL, write the operation with a name and a variable:

```graphql
mutation AddOwner($input: AddOwnerInput!) {
  addOwner(input: $input) {
    id
    firstName
    lastName
  }
}
```

Open the **Variables** tab below the editor and give the variable a value, as JSON:

```json
{
  "input": {
    "firstName": "Ana",
    "lastName": "Lozano",
    "address": "Calle Larios 3",
    "city": "Málaga",
    "telephone": "9521234567"
  }
}
```

Run it, and Ana Lozano is owner 16.

| Syntax | Meaning |
|---|---|
| `mutation AddOwner(...)` | An **operation name**. Logs, tools and the frontend tell operations apart by it. |
| `$input: AddOwnerInput!` | A **variable** with its type. The engine checks the value against the type, as it checks a literal. |
| `addOwner(input: $input)` | Uses the variable where a literal value would go. |

Every operation the frontend sends is written this way: open `src/main/resources/operations/mutations/addOwner.graphql`. The text of the operation stays the same, and only the variables change. Chapter 11 relies on that.

Leave `telephone` out of the variables and run it again. The engine refuses the input, because the schema says the field is required:

```text
Variable 'input' has an invalid value: Field 'telephone' has coerced Null value for NonNull type 'String!'
```

Then send a telephone of `"123"`. That value is a valid `String`, so the engine passes it to our code, and the service refuses it. The answer is an `INTERNAL_ERROR`, with the reason hidden in the log. Chapter 4 turns it into an error a client can act on.

## 4. Partial updates: correct an owner's details

An update should change the fields the client sends and keep the rest. Make every field optional except the id:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Mutation {
    # ...existing mutations...
    updateOwner(input: UpdateOwnerInput!): Owner!
}

"The fields to change. A field left out keeps its value."
input UpdateOwnerInput {
    id: ID!
    firstName: String
    lastName: String
    address: String
    city: String
    telephone: String
}
```

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
@MutationMapping
Owner updateOwner(@Argument UpdateOwnerInput input) {
	return ownerService.updateOwner(input);
}
```

Restart and move Ana to Nerja:

```graphql
mutation {
  updateOwner(input: { id: 16, city: "Nerja" }) {
    firstName
    lastName
    city
    telephone
  }
}
```

Her city changes, and her name and telephone stay as they were.

GraphQL tells apart a field the client **left out** and a field it **set to `null`**. `OwnerService.updateOwner` treats both as "keep the value", because every field of an owner is required. When the difference matters, for example to clear an optional field, Spring for GraphQL can bind a field to an `ArgumentValue`, which says whether the client sent it.

## 5. The forms light up

Refresh the frontend. The owner pages now have **Add owner** and **Edit owner**, and every pet has **Book visit**. Book a visit: the form takes you back to the owner's page, which lists the new visit under the pet.

## Exercises

### Exercise 1: add a pet

The **Add pet** form needs two operations: a `petTypes` query for its list of types, and an `addPet` mutation. Write both. `PetService` has `findPetTypes()` and `addPet(AddPetInput)`, and `AddPetInput` has `ownerId`, `name`, `birthDate` and `typeId`. Put them in a new `PetGraphQLController` in the `owner.pet` package. Then add a pet in the frontend.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    petTypes: [PetType!]!
}

type Mutation {
    # ...existing mutations...
    addPet(input: AddPetInput!): Pet!
}

input AddPetInput {
    ownerId: ID!
    name: String!
    birthDate: Date!
    typeId: ID!
}
```

`src/main/java/org/springframework/samples/petclinic/owner/pet/PetGraphQLController.java`

```java
package org.springframework.samples.petclinic.owner.pet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
class PetGraphQLController {

	private final PetService petService;

	PetGraphQLController(PetService petService) {
		this.petService = petService;
	}

	@QueryMapping
	List<PetType> petTypes() {
		return petService.findPetTypes();
	}

	@MutationMapping
	Pet addPet(@Argument AddPetInput input) {
		return petService.addPet(input);
	}

}
```

</details>

### Exercise 2: edit a pet

Add an `updatePet` mutation that changes the fields it gets and keeps the rest, like `updateOwner`. `PetService.updatePet` takes an `UpdatePetInput` with `id`, `name`, `birthDate` and `typeId`. Then rename a pet in the frontend, and try giving it the name of its sibling: you get an `INTERNAL_ERROR`, which chapter 4 improves.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Mutation {
    # ...existing mutations...
    updatePet(input: UpdatePetInput!): Pet!
}

"The fields to change. A field left out keeps its value."
input UpdatePetInput {
    id: ID!
    name: String
    birthDate: Date
    typeId: ID
}
```

`src/main/java/org/springframework/samples/petclinic/owner/pet/PetGraphQLController.java`

```java
@MutationMapping
Pet updatePet(@Argument UpdatePetInput input) {
	return petService.updatePet(input);
}
```

</details>

## Recap

- A custom scalar gives a value a type and rules: `Date` accepts `2026-10-30` and refuses `30/10/2026`.
- Mutations live in the `Mutation` root type, and `@MutationMapping` binds them.
- `input` types group the values of an argument, and Spring for GraphQL fills a matching record.
- Variables keep the operation fixed and send the values separately.
- An update with optional fields changes the fields the client sends, and keeps the others as they are.

Chapter 4 turns exceptions into errors a client can act on.
