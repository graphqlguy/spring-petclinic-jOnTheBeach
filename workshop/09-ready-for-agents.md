# Chapter 9: Ready for agents: descriptions and query limits

> **User story** · As the clinic manager, I want AI assistants to use our API correctly and safely, so that they help the staff without guessing and without overloading the server.

In the second half of the workshop, the client is an AI agent. It reads our schema the way GraphiQL does, through introspection, and decides what to call from the names and the descriptions alone. A developer who is unsure can ask a colleague; an agent acts on what the schema says. Before we hand the API over, we make the descriptions say what an agent needs, and we limit what a single query may cost.

By the end of this chapter, you will:

- Know what an agent needs from a description
- Describe queries, mutations, arguments and inputs so that a caller can act on them
- Read the descriptions the way an agent does, through introspection
- Limit the depth and the size of a query

## 1. What an agent reads

Every description we wrote reaches the agent, the notes we wrote for ourselves included: `Owner`'s description still says *Triple quotes allow a description over several lines*. A good description answers the questions a caller has before it calls:

| Say | Example |
|---|---|
| Where an id comes from | "owners and search give the ids of owners" |
| What `null` or an unknown id means | "or null when no owner has the id", "returns a NOT_FOUND error" |
| What a repeated call does | "Every call books one more visit" |
| How to write a value | "written as 2026-10-21", "ten digits" |
| The rules a call must follow | "An owner cannot have two pets with the same name" |
| Who may call it | "Every mutation except login needs a staff token" |

An agent that knows a call creates a new visit each time checks before it retries. One that knows where a `petId` comes from calls `owner` or `search` first, and avoids guessing a number.

## 2. Describe the schema

Replace the whole content of `schema.graphqls` with the version below. It keeps every type and field of chapters 1 to 8, and adds the descriptions an agent needs:

- `Query` and `Mutation` get a description of what holds for all their fields, such as who may call and what an error means.
- Each query and mutation says where its ids come from and what a second call does.
- The arguments of `owners` get descriptions too. A description goes right before its argument, so the arguments now stand one per line.
- The fields of `AddVisitInput` and `AddPetInput`, which an agent fills in, say how to write each value.
- `Owner` gets one line, in place of the note for us.

`src/main/resources/graphql/schema.graphqls`

```graphql
scalar Date @specifiedBy(url: "https://tools.ietf.org/html/rfc3339")
directive @PositiveOrZero(message: String = "graphql.validation.PositiveOrZero.message") on ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION
directive @Range(min: Int = 0, max: Int = 2147483647, message: String = "graphql.validation.Range.message") on ARGUMENT_DEFINITION | INPUT_FIELD_DEFINITION

# The entry point of our API. This line is a comment:
# comments start with # and are only for people reading this file.
"""
Reads the clinic's data. Anyone may read it, except an owner's telephone, which needs a staff token.
An id identifies an object within its type, so an owner and a pet can have the same id.
"""
type Query {
    "All owners, from all pages, without filters"
    ownersAll: [Owner!]! @deprecated(reason: "Unbounded. Use 'owners' instead.")

    "One owner with their pets, or null when no owner has the id. owners and search give the ids of owners."
    owner(id: ID!): Owner

    """
    Owners, one page at a time, sorted by last name unless sort says otherwise.
    A page past the last one holds an empty list.
    """
    owners(
        "Narrows the list by the start of the last name and by city. Leave it out to list every owner."
        filter: OwnerFilter
        "The page to return. Pages count from 0."
        page: Int! = 0 @PositiveOrZero
        "The number of owners on a page, from 1 to 100"
        size: Int! = 10 @Range(min: 1, max: 100)
        sort: OwnerSort
    ): OwnerPage!

    "Every vet of the clinic, with their specialties"
    vets: [Vet!]!

    "One vet with their visits. An id that no vet has returns a NOT_FOUND error. vets and search give the ids of vets."
    vet(id: ID!): Vet

    "The kinds of pet the clinic treats, sorted by name. addPet and updatePet take their ids as typeId."
    petTypes: [PetType!]!

    """
    Owners, vets and pets whose name contains the text, in upper or lower case.
    __typename says whether a result is an Owner, a Vet or a Pet.
    """
    search(text: String!): [SearchResult!]!

    "Owners and vets whose first or last name contains the text, in upper or lower case"
    people(name: String!): [Person!]!
}

"Something a search can find"
union SearchResult = Owner | Pet | Vet

"A person the clinic knows: an owner or a vet"
interface Person {
    id: ID!
    firstName: String!
    lastName: String!
}

"An owner of one or more pets, and a client of the clinic"
type Owner implements Person {
    id: ID!
    firstName: String!
    lastName: String!
    address: String!
    city: String!
    "Ten digits, such as 9521234567. Only staff can read it."
    telephone: String
    pets: [Pet!]!
}

"A pet of an owner, and a patient of the clinic"
type Pet {
    id: ID!
    name: String!
    birthDate: Date
    type: PetType!
    owner: Owner!
    "The visits of this pet, oldest first"
    visits: [Visit!]!
}

"A kind of pet, such as cat or dog"
type PetType {
    id: ID!
    name: String!
}

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

"A veterinarian of the clinic"
type Vet implements Person {
    id: ID!
    firstName: String!
    lastName: String!
    specialties: [Specialty!]!
    "The visits this vet has seen or will see, newest first"
    visits: [Visit!]!
}

"A visit of a pet to the clinic, past or booked"
type Visit {
    id: ID!
    date: Date!
    description: String!
    pet: Pet!
    "The vet who sees the pet. The Petclinic web pages book visits without a vet."
    vet: Vet
}

"A field a vet specializes in, such as surgery"
type Specialty {
    id: ID!
    name: String!
}

"Events a client can listen to, over a WebSocket or Server-Sent Events"
type Subscription {
    "Each visit as it is booked"
    visitBooked: Visit!
}

"""
Changes the clinic's data. Every mutation except login needs a staff token.
A value that breaks a rule returns one BAD_REQUEST error for each field, and the data stays as it was.
An id that does not exist returns a NOT_FOUND error.
"""
type Mutation {
    """
    Books a visit for a pet. Every call books one more visit, so after a call that failed or timed out,
    check the pet's visits before calling again.
    """
    addVisit(input: AddVisitInput!): Visit!

    """
    Registers a new owner. Every call registers one more owner, so calling it twice with the same input
    creates two owners.
    """
    addOwner(input: AddOwnerInput!): Owner!

    "Changes the details of an owner. Calling it again with the same input leaves the owner as it is."
    updateOwner(input: UpdateOwnerInput!): Owner!

    """
    Registers a new pet for an owner. An owner cannot have two pets with the same name,
    so a second call with the same name is refused.
    """
    addPet(input: AddPetInput!): Pet!

    "Changes the details of a pet. Calling it again with the same input leaves the pet as it is."
    updatePet(input: UpdatePetInput!): Pet!

    """
    Signs in, and returns the access token to send as 'Authorization: Bearer <token>'.
    A wrong username or password returns an UNAUTHORIZED error.
    """
    login(username: String!, password: String!): String!
}

input AddVisitInput {
    "The pet to see. owner and search give the ids of pets."
    petId: ID!
    "The vet who sees the pet, from vets. Leave it out to book the visit without a vet."
    vetId: ID
    "The day of the visit, written as 2026-10-21. Leave it out to book the visit for tomorrow."
    date: Date
    "Why the pet comes, up to 255 characters"
    description: String!
}

input AddOwnerInput {
    firstName: String!
    lastName: String!
    address: String!
    city: String!
    "Ten digits, such as 9521234567"
    telephone: String!
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

input AddPetInput {
    "The owner of the new pet. owners and search give the ids of owners."
    ownerId: ID!
    "A name the owner does not already use for another pet"
    name: String!
    "Written as 2024-05-01, today or earlier"
    birthDate: Date!
    "The kind of pet, from petTypes"
    typeId: ID!
}

"The fields to change. A field left out keeps its value."
input UpdatePetInput {
    id: ID!
    name: String
    birthDate: Date
    typeId: ID
}
```

Restart, and ask the schema for the mutations and their descriptions, as an agent's tools do:

```graphql
query {
  __type(name: "Mutation") {
    fields {
      name
      description
    }
  }
}
```

```json
{
  "name": "addVisit",
  "description": "Books a visit for a pet. Every call books one more visit, so after a call that failed or timed out,\ncheck the pet's visits before calling again."
}
```

## 3. Limit what one query may cost

A client may follow the graph as deep as it likes. This query asks for every owner's pets' owner's pets, four times over, and an agent that explores freely can write one like it:

```graphql
query {
  owners(size: 100) {
    content {
      pets {
        owner {
          pets {
            owner {
              pets {
                owner {
                  pets {
                    name
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

GraphQL Java has two limits that refuse such a query before any of it runs: a maximum **depth**, counted in fields from the root, and a maximum **complexity**, counted as the number of fields selected at every level. Spring Boot adds every `Instrumentation` bean to the engine. **Create a new class** named `GraphQLLimitsConfiguration` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLLimitsConfiguration.java`

```java
package org.springframework.samples.petclinic.system;

import graphql.ExecutionResult;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.Field;
import graphql.language.OperationDefinition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GraphQLLimitsConfiguration {

	@Bean
	MaxQueryDepthInstrumentation maxQueryDepth() {
		return new MaxQueryDepthInstrumentation(5) {

			// Introspection reads the schema, whose nesting is fixed, and GraphQL Java
			// checks it already, so the depth limit applies to the data alone
			@Override
			public InstrumentationContext<ExecutionResult> beginExecuteOperation(
					InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
				if (isIntrospection(parameters.getExecutionContext().getOperationDefinition())) {
					return SimpleInstrumentationContext.noOp();
				}
				return super.beginExecuteOperation(parameters, state);
			}

		};
	}

	@Bean
	MaxQueryComplexityInstrumentation maxQueryComplexity() {
		return new MaxQueryComplexityInstrumentation(200);
	}

	private static boolean isIntrospection(OperationDefinition operation) {
		return operation.getSelectionSet()
			.getSelections()
			.stream()
			.allMatch((selection) -> selection instanceof Field field && field.getName().startsWith("__"));
	}

}
```

| Element | Meaning |
|---|---|
| `MaxQueryDepthInstrumentation(5)` | Refuses a query nested more than 5 fields deep. The owner and vet pages send the deepest operations of the frontend, such as `owner { pets { visits { vet { firstName } } } }`, with 5 levels, so the limit lets every page through and refuses anything deeper. |
| `beginExecuteOperation` override | The introspection query that GraphiQL and the frontend send to read the schema is 15 levels deep, and the limit would refuse it. An operation whose root fields all start with `__` reads only the schema, so it skips the depth check. |
| `MaxQueryComplexityInstrumentation(200)` | Refuses a query that selects more than 200 fields, counted at every level. |

Restart and run the deep query:

```json
{
  "errors": [
    {
      "message": "maximum query depth exceeded 10 > 5",
      "extensions": { "classification": "ExecutionAborted" }
    }
  ]
}
```

A query that selects the same field 210 times under aliases, `owner(id: 1) { a1: firstName a2: firstName ... }`, is refused by the other limit: `maximum query complexity exceeded 211 > 200`, one field for `owner` and 210 for the aliases. Refresh the frontend: every page still works, because its operations stay within both limits.

GraphQL Java guards introspection on its own, which is why the depth limit leaves it out: ask for `__type` twice in one request, and it answers `This request is not asking for introspection in good faith - Query.__type is present too often!`.

## Recap

- An agent reads the schema through introspection and decides from its names and descriptions.
- A description says where ids come from, what `null` means, what a repeated call does, how to write values, and who may call.
- An argument can have its own description.
- GraphQL Java guards introspection itself, and refuses a request that asks `__type` more than once.
- Depth and complexity limits refuse an expensive query before any of it runs.

That completes the GraphQL half. Chapter 10 hands this API to an AI agent, as MCP tools.
