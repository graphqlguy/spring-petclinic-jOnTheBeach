# Chapter 9: Ready for agents: descriptions and query limits

> **User story** · As the clinic manager, I want AI assistants to use our API correctly and safely, so that they help the staff without guessing and without overloading the server.

In the second half of the workshop, the client is an AI agent. It reads our schema the way GraphiQL does, through introspection, and decides what to call from the names and the descriptions alone. A developer who is unsure can ask a colleague; an agent acts on what the schema says. Before we hand the API over, we make the descriptions say what an agent needs, and we limit what a single query may cost.

By the end of this chapter, you will:

- Know what an agent needs from a description
- Describe queries, mutations and arguments so that a caller can act on them
- Read the result the way an agent does, through introspection
- Limit the depth and the size of a query

## 1. What an agent reads

Every description we wrote reaches the agent. Some were written for us: `Owner`'s description still says *Triple quotes allow a description over several lines*, a note for us that every caller now reads. Replace it, and the comment between it and the type, with one line:

`src/main/resources/graphql/schema.graphqls`

```graphql
"An owner of one or more pets, and a client of the clinic"
type Owner implements Person {
```

A good description answers the questions a caller has before it calls:

| Say | Example |
|---|---|
| Where an id comes from | "owners and search give the ids of owners" |
| What `null` or an unknown id means | "or null when no owner has the id", "returns a NOT_FOUND error" |
| What a repeated call does | "Every call books one more visit" |
| How to write a value | "written as 2026-10-21", "ten digits" |
| The rules a call must follow | "An owner cannot have two pets with the same name" |
| Who may call it | "Every mutation except login needs a staff token" |

An agent that knows a call creates a new visit each time checks before it retries. One that knows where a `petId` comes from calls `owner` or `search` first, and avoids guessing a number.

## 2. Describe the queries

GraphQL allows a description on a type, a field, and each argument. Replace the whole `Query` type, from its description to its closing brace:

`src/main/resources/graphql/schema.graphqls`

```graphql
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

    """
    The visits from one date to another, both included, in date order, past and booked.
    Use it for questions such as which visits are booked for next week.
    """
    visits(
        "The first day, written as 2026-10-21"
        from: Date!
        "The last day, on or after from"
        to: Date!
    ): [Visit!]!

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
```

An argument's description goes right before the argument, which is why `owners` and `visits` now list one argument per line.

## 3. Describe the mutations

Replace the whole `Mutation` type the same way:

`src/main/resources/graphql/schema.graphqls`

```graphql
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
```

The type's own description states what holds for every mutation: who may call, and what an error means. Each field adds what is particular to it: whether a second call repeats the change.

## 4. Read the schema as an agent does

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

Ask for two types in one request, with `__type` twice, and GraphQL Java refuses: `This request is not asking for introspection in good faith - Query.__type is present too often!`. It guards introspection against queries built to make it expensive, so a client asks one type at a time, or reads the whole schema with a single `__schema`.

## 5. Limit what one query may cost

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
		return new MaxQueryDepthInstrumentation(8) {

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
| `MaxQueryDepthInstrumentation(8)` | Refuses a query nested more than 8 fields deep. The deepest operations of the frontend have 5 levels. |
| `beginExecuteOperation` override | The introspection query that GraphiQL and the frontend send to read the schema is 15 levels deep, and the limit would refuse it. An operation whose root fields all start with `__` reads only the schema, so it skips the depth check. |
| `MaxQueryComplexityInstrumentation(200)` | Refuses a query that selects more than 200 fields, counted at every level. |

Restart and run the deep query:

```json
{
  "errors": [
    {
      "message": "maximum query depth exceeded 10 > 8",
      "extensions": { "classification": "ExecutionAborted" }
    }
  ]
}
```

A query that selects the same field 210 times under aliases, `a1: firstName a2: firstName ...`, is refused by the other limit: `maximum query complexity exceeded 210 > 200`. Refresh the frontend: every page still works, because its operations stay well within both limits.

## Exercise: describe the inputs

An agent fills in input types as well. Describe the fields of `AddVisitInput` and `AddPetInput` with the questions of step 1 in mind: where each id comes from, how to write the values, and the rules that apply.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
input AddVisitInput {
    "The pet to see. owner, search and visits give the ids of pets."
    petId: ID!
    "The vet who sees the pet, from vets. Leave it out to book the visit without a vet."
    vetId: ID
    "The day of the visit, written as 2026-10-21. Leave it out to book the visit for tomorrow."
    date: Date
    "Why the pet comes, up to 255 characters"
    description: String!
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
```

</details>

## Recap

- An agent reads the schema through introspection and decides from its names and descriptions.
- A description says where ids come from, what `null` means, what a repeated call does, how to write values, and who may call.
- An argument can have its own description.
- GraphQL Java guards introspection itself, and refuses a request that asks `__type` more than once.
- Depth and complexity limits refuse an expensive query before any of it runs.

That completes the GraphQL half. Chapter 10 hands this API to an AI agent, as MCP tools.
