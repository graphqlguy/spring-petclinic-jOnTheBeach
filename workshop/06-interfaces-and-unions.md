# Chapter 6: Interfaces and unions

> **User story** · As the receptionist, I want one search box for owners, vets and pets, so that I find a record from whatever name a client gives me.

Every field so far returns one type. A search across the clinic returns owners, vets and pets in a single list. GraphQL has two abstract types for this: a **union**, for types that have little in common, and an **interface**, for types that share fields. In this chapter we use both.

By the end of this chapter, you will:

- Return objects of different types from one field, with a union
- Read them with inline fragments and `__typename`
- Share fields between types with an interface
- Choose between a union and an interface

## 1. One search, three types

Declare the query and a union of the three types it can return:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    "Owners, vets and pets whose name contains the text, in upper or lower case"
    search(text: String!): [SearchResult!]!
}

"Something a search can find"
union SearchResult = Owner | Pet | Vet
```

The services already search each type. A search spans the clinic, so it gets a package of its own: **create a new class** named `SearchGraphQlController` in a new `org.springframework.samples.petclinic.search` package:

`src/main/java/org/springframework/samples/petclinic/search/SearchGraphQlController.java`

```java
package org.springframework.samples.petclinic.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.samples.petclinic.owner.OwnerService;
import org.springframework.samples.petclinic.owner.PetService;
import org.springframework.samples.petclinic.vet.VetService;
import org.springframework.stereotype.Controller;

@Controller
class SearchGraphQlController {

	private final OwnerService ownerService;

	private final VetService vetService;

	private final PetService petService;

	SearchGraphQlController(OwnerService ownerService, VetService vetService, PetService petService) {
		this.ownerService = ownerService;
		this.vetService = vetService;
		this.petService = petService;
	}

	@QueryMapping
	List<Object> search(@Argument String text) {
		List<Object> results = new ArrayList<>();
		results.addAll(this.ownerService.search(text));
		results.addAll(this.vetService.search(text));
		results.addAll(this.petService.search(text));
		return results;
	}

}
```

The method returns plain Java objects. For each one, GraphQL has to know which type of the union it is, and Spring for GraphQL decides by the class name: an `Owner` object is an `Owner`, a `Pet` a `Pet`, and a `Vet` a `Vet`.

## 2. Query a union with inline fragments

Restart and try the obvious query:

```graphql
query {
  search(text: "le") {
    name
  }
}
```

The engine refuses it: `Field 'name' in type 'SearchResult' is undefined`. A union only lists its member types, whatever fields they have. The client asks for fields **per type**, with **inline fragments**, and asks `__typename` to learn the type of each result:

```graphql
query {
  search(text: "le") {
    __typename
    ... on Owner {
      firstName
      lastName
      city
    }
    ... on Vet {
      firstName
      lastName
    }
    ... on Pet {
      name
      type {
        name
      }
    }
  }
}
```

```json
{
  "data": {
    "search": [
      { "__typename": "Owner", "firstName": "Alejandro", "lastName": "Blanco", "city": "Rincón de la Victoria" },
      { "__typename": "Owner", "firstName": "Pablo", "lastName": "Morales", "city": "Marbella" },
      { "__typename": "Owner", "firstName": "Elena", "lastName": "Serrano", "city": "Málaga" },
      { "__typename": "Vet", "firstName": "Helen", "lastName": "Leary" },
      { "__typename": "Pet", "name": "Galleta", "type": { "name": "hamster" } },
      { "__typename": "Pet", "name": "Leo", "type": { "name": "cat" } }
    ]
  }
}
```

| Syntax | Meaning |
|---|---|
| `... on Owner { ... }` | An inline fragment: these fields, for results that are owners. |
| `__typename` | A field every type has: the name of the object's type. A client uses it to tell the results apart. |

## 3. An interface for what owners and vets share

Owners and vets both have a first and a last name; in Java, both extend Petclinic's `Person` class. The query above asks for those two fields twice. An **interface** declares shared fields once:

`src/main/resources/graphql/schema.graphqls`

```graphql
"A person the clinic knows: an owner or a vet"
interface Person {
    id: ID!
    firstName: String!
    lastName: String!
}
```

Then declare that both types implement it. Change their first lines:

```graphql
type Owner implements Person {
```

```graphql
type Vet implements Person {
```

A type that implements an interface must have all of the interface's fields, and `Owner` and `Vet` have them already. Restart. One fragment on the interface now covers both kinds of person:

```graphql
query {
  search(text: "le") {
    __typename
    ... on Person {
      firstName
      lastName
    }
    ... on Pet {
      name
    }
  }
}
```

Refresh the frontend: a search box appears in the navigation bar. Search for `le`: the page lists owners, a vet and pets, each linked to its page. Its operation is `src/main/resources/operations/queries/search.graphql`, which uses both the union and the interface.

## 4. Union or interface?

| | Union | Interface |
|---|---|---|
| Members share fields | Not needed | Required: each member has every field of the interface |
| Query its fields directly | Only `__typename` | Every shared field, without a fragment |
| Good for | Results of unrelated types, such as a search | Types that are kinds of the same thing, such as owners and vets |

A type can belong to several unions and implement several interfaces, so the two combine: `SearchResult` is a union, and two of its members are also `Person`s.

## Exercise: find people by name

Add a `people(name: String!)` query that returns owners and vets whose name contains the text, typed as the interface: `[Person!]!`. Use `OwnerService.search` and `VetService.search`, in `SearchGraphQlController`. Then ask for the shared fields directly, and for the city of the owners and the specialties of the vets with fragments.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
    # ...existing queries...
    "Owners and vets whose first or last name contains the text, in upper or lower case"
    people(name: String!): [Person!]!
}
```

`src/main/java/org/springframework/samples/petclinic/search/SearchGraphQlController.java`

```java
import org.springframework.samples.petclinic.model.Person;

@QueryMapping
List<Person> people(@Argument String name) {
	List<Person> people = new ArrayList<>();
	people.addAll(this.ownerService.search(name));
	people.addAll(this.vetService.search(name));
	return people;
}
```

```graphql
query {
  people(name: "le") {
    __typename
    firstName
    lastName
    ... on Owner {
      city
    }
    ... on Vet {
      specialties {
        name
      }
    }
  }
}
```

`firstName` and `lastName` work without a fragment, because every `Person` has them.

</details>

## Recap

- A union returns objects of unrelated types from one field.
- Inline fragments ask for fields per type, and `__typename` tells the results apart.
- An interface declares the fields its types share, and a query asks for them without a fragment.
- Spring for GraphQL picks each object's GraphQL type from its Java class name.

Chapter 7 pushes new visits to clients as they are booked.
