# Chapter 2: Relationships and the N+1 problem

> **User story** · As a vet, I want to see each patient with its owner and its visit history, and the visits on my own list, so that I know a pet before it walks in.

The clinic's data is a graph: owners have pets, pets have visits, and each visit has a vet. In this chapter we put those relationships in the schema and learn how GraphQL resolves nested fields, first by itself and then with our own code. Then we find, and fix, the performance trap that comes with them.

By the end of this chapter, you will:

- Expose relationships as nested fields
- Understand default field resolution: how GraphQL finds a value without any resolver code
- Write a `@SchemaMapping` resolver for a field without a matching Java property
- Reproduce the N+1 problem and see exactly where it comes from
- Remove it with `@BatchMapping`, with the schema unchanged

## 1. Add pets to Owner

The `Owner` entity already has its pets: a lazy `@OneToMany` list with a `getPets()` getter. The schema does not mention them yet. Add a `pets` field to `Owner`, and two new types below it:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Owner {
    # ...existing fields...
    pets: [Pet!]!
}

"A pet of an owner, and a patient of the clinic"
type Pet {
    id: ID!
    name: String!
    birthDate: String
    type: PetType!
}

"A kind of pet, such as cat or dog"
type PetType {
    id: ID!
    name: String!
}
```

`birthDate` is a `String` for now. Chapter 3 gives dates a type of their own.

Restart the application and run a nested query in GraphiQL:

```graphql
query {
  owner(id: 12) {
    firstName
    lastName
    pets {
      name
      birthDate
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
    "owner": {
      "firstName": "Lucía",
      "lastName": "Navarro",
      "pets": [
        { "name": "Nala", "birthDate": "2023-09-30", "type": { "name": "cat" } },
        { "name": "Simba", "birthDate": "2020-01-22", "type": { "name": "cat" } }
      ]
    }
  }
}
```

It works without a line of Java. Refresh the frontend and open an owner: the owner page now lists the pets, and their visits follow in the exercise.

### Why does this work?

GraphQL needs a resolver for every field in a query: a piece of code that produces its value. Without one of ours, the engine uses its **default resolver**, which looks at the parent object and calls the getter with the field's name. `owner` returns an `Owner` entity, the entity has `getPets()`, so `pets` resolves by itself, and so do `name`, `birthDate` and `type` on each `Pet`. Every field of chapter 1 resolved the same way.

The pets are a lazy relationship: Hibernate reads them from the database when `getPets()` is called. The starting point keeps the persistence context open for the whole HTTP request (`spring.jpa.open-in-view=true` in `application.properties`), so the getter can still load them while GraphQL builds the response.

A field needs code of your own only when the parent object cannot answer for it.

## 2. The other direction: the visits of a vet

A vet sees many visits. In Java, the relationship belongs to `Visit`, which has a `getVet()`, and `Vet` lacks a `getVisits()`. Add the field and the `Visit` type to the schema anyway:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Vet {
    # ...existing fields...
    "The visits this vet has seen or will see, newest first"
    visits: [Visit!]!
}

"A visit of a pet to the clinic, past or booked"
type Visit {
    id: ID!
    date: String!
    description: String!
}
```

Restart. The startup report has found the gap already:

```text
GraphQL schema inspection:
	Unmapped fields: {Vet=[visits]}
```

Run the query anyway:

```graphql
query {
  vet(id: 3) {
    lastName
    visits {
      date
      description
    }
  }
}
```

The answer is the error from chapter 1, `The field at path '/vet/visits' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value`, with `"data": {"vet": null}`. The default resolver did not find a `getVisits()`, so it returned `null`. `visits` promises a list, so GraphQL sets its parent to `null` instead: the nearest field that may be `null` is `vet`.

This is the moment for our first **schema mapping**: a controller method that resolves one field of one type. `VisitService.findByVet` is ready. Replace the whole `VetGraphQLController` with this version, which adds the `VisitService` and the new method:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.samples.petclinic.owner.visit.Visit;
import org.springframework.samples.petclinic.owner.visit.VisitService;
import org.springframework.stereotype.Controller;

@Controller
class VetGraphQLController {

	private final VetService vetService;

	private final VisitService visitService;

	VetGraphQLController(VetService vetService, VisitService visitService) {
		this.vetService = vetService;
		this.visitService = visitService;
	}

	@QueryMapping
	List<Vet> vets() {
		return vetService.findAll();
	}

	@QueryMapping
	Vet vet(@Argument Integer id) {
		return vetService.findById(id);
	}

	@SchemaMapping
	List<Visit> visits(Vet vet) {
		return visitService.findByVet(vet);
	}

}
```

| Element | Meaning |
|---|---|
| `@SchemaMapping` | Binds the method to a field. By convention, the method name is the field (`visits`), and the parameter's type is the type the field belongs to (`Vet`). |
| `Vet vet` | The parent object, as the resolver above it produced it. GraphQL hands it over, so that we can resolve the child field. |

Restart and run the query again: Linda Douglas has 7 visits. `@QueryMapping`, from chapter 1, is a shorthand for `@SchemaMapping` on the `Query` type.

Refresh the frontend and open a vet from the **Veterinarians** page: the vet page now lists the visits, and step 5 adds each visit's pet and owner.

## 3. The hidden cost: N+1

Think about *when* our resolver runs: once per vet. Ask for one vet and its visits, and it runs once. Ask for all six vets and their visits, and it runs six times, each time with its own database query. One query for the list, plus N queries for the nested field: this is the **N+1 problem**, the classic performance trap of GraphQL APIs.

On a laptop with an in-memory database, seven queries finish in milliseconds, so the cost is invisible until we measure it. The starting point has two switches for that. In `application.properties`, change these two values:

`src/main/resources/application.properties`

```properties
spring.jpa.show-sql=true
```

```properties
petclinic.demo.latency=1s
```

The first prints every SQL statement. The second makes every service method pause for one second before it touches the database, as a round trip to a remote database would.

Restart, and run:

```graphql
query {
  vets {
    lastName
    visits {
      date
    }
  }
}
```

The cost is plain now:

- The response takes about **seven seconds**: one paused service call for the vets, then six more, one per vet, for `visits`.
- The console shows six statements that differ only in the vet's id:

```text
Hibernate: select v1_0.id,v1_0.visit_date,v1_0.description,v1_0.pet_id,v1_0.vet_id from visits v1_0 where v1_0.vet_id=? order by v1_0.visit_date desc,v1_0.id desc
```

The first time, you also see the statements that read the vets and their specialties. After that, Petclinic serves the vets from a cache.

Every resolver did its job correctly. The trouble is that each one sees a single vet: our `visits` resolver cannot tell that its six calls could have been a single `where vet_id in (...)` query.

Scale the numbers up, and the cost becomes serious: 100 vets against a real database at 5 milliseconds per round trip is half a second of waiting for one page, and every further level of nesting multiplies it. Keep this query: the next step fixes it with the schema unchanged.

## 4. The fix: batch loading with @BatchMapping

The fix is to collect **all** the vets of the current request first, then load their visits in one call. GraphQL calls this pattern a **DataLoader**, and Spring for GraphQL offers it as `@BatchMapping`.

The data access is in the starting point, because it is repository code. `VisitRepository.findByVetIds` loads the visits of many vets with `where visit.vet.id in :vetIds`, and `VisitService.findVisitsByVetIds` groups them into a `Map<Integer, List<Visit>>` keyed by vet id. Read it before you go on: it pauses once, however many vets it gets.

Replace the `@SchemaMapping` method from step 2 with this one, and replace the `SchemaMapping` import with the three new ones:

`src/main/java/org/springframework/samples/petclinic/vet/VetGraphQLController.java`

```java
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.BatchMapping;

@BatchMapping
Map<Vet, List<Visit>> visits(List<Vet> vets) {
	List<Integer> vetIds = vets.stream().map(Vet::getId).toList();
	Map<Integer, List<Visit>> visitsByVetId = visitService.findVisitsByVetIds(vetIds);
	return vets.stream()
		.collect(Collectors.toMap((vet) -> vet, (vet) -> visitsByVetId.getOrDefault(vet.getId(), List.of())));
}
```

Compare the two signatures:

| Step 2: `@SchemaMapping` | Step 4: `@BatchMapping` |
|---|---|
| `List<Visit> visits(Vet vet)` | `Map<Vet, List<Visit>> visits(List<Vet> vets)` |
| Called once per vet | Called once per request |
| Returns one vet's visits | Returns every vet's visits, keyed by vet |

Behind the scenes, Spring registers a DataLoader for the field. GraphQL collects every `Vet` that needs `visits`, hands the whole batch to our method in one call, and gives each vet its list from the map. A vet missing from the map gets the `getOrDefault` value, an empty list.

Restart and run the exact query from step 3 again:

- The response time drops from about **seven seconds to two**: one paused call for the vets, and one for all their visits.
- The console shows one statement for the visits, with all six ids: `... from visits v1_0 where v1_0.vet_id in (?,?,?,?,?,?) order by ...`
- The application log has one line for the batch: `Batch fetching visits for 6 vets`.

### The same trap behind a getter

Lazy relationships that GraphQL reads through getters, such as `Owner.pets`, have the same problem: one query per owner. Petclinic's `application.properties` already sets `spring.jpa.properties.hibernate.default_batch_fetch_size=16`, so Hibernate loads the pets of up to 16 owners in one statement. Try it while the SQL log is on:

```graphql
query {
  owners(size: 10) {
    content {
      lastName
      pets {
        name
      }
    }
  }
}
```

The console shows three statements: the page of owners, its count, and one `... from pets p1_0 ... where p1_0.owner_id in (?,?,...)` for the pets of all ten. `@BatchMapping` batches the resolvers we write, and Hibernate batches the lazy relationships behind the getters.

> [!NOTE]
> Hibernate's batch fetching works here because `spring.jpa.open-in-view=true` keeps the persistence context open while GraphQL calls the getters. With it set to `false`, as many applications run, the same query fails with a `LazyInitializationException`. Batch fetching also takes one statement for every 16 owners, so a page of 100 owners needs 7 statements for their pets. To fix the trap properly, resolve each lazy relationship with a `@BatchMapping`: it works with open-in-view off, and loads the field for every parent of the request in one call. The workshop keeps the getters to save time.

### Turn the instrumentation off

Set the two properties back, so that the rest of the workshop runs at full speed:

`src/main/resources/application.properties`

```properties
spring.jpa.show-sql=false
```

```properties
petclinic.demo.latency=0s
```

## 5. Follow the graph both ways

So far each relationship points one way: an owner lists their pets, and a vet lists their visits. The vet page also needs the pet of each visit and the pet's owner, to show who came in. In GraphQL a relationship is a field, so a field in the other direction lets a client follow the links it needs in one query, where a REST client would send one more request per visit.

The starting point gave `Pet` a `getOwner()` and `Visit` a `getPet()`, so both directions resolve through getters. Add the two fields:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Pet {
    # ...existing fields...
    owner: Owner!
}

type Visit {
    # ...existing fields...
    pet: Pet!
}
```

Restart, and follow the graph from a vet to the owners of their patients:

```graphql
query {
  vet(id: 3) {
    lastName
    visits {
      date
      description
      pet {
        name
        owner {
          firstName
          lastName
        }
      }
    }
  }
}
```

The first visit is a dental cleaning for Pelusa, Marta Ramos's cat. Refresh the frontend and open a vet again: each visit now shows its pet and the pet's owner.

A client can follow these fields as deep as it likes, `owner { pets { owner { pets ... } } }`, and the server answers every level. Chapter 9 puts a limit on that.

## Exercise: a pet's visits and their vets

The owner page in the frontend shows each pet's visits, with the vet who saw it, once the schema has them. `Pet` has `getVisits()`, oldest first, and `Visit` has `getVet()`. Add a `visits` field to `Pet` and a `vet` field to `Visit`, and decide which one may be `null`: the Petclinic web pages book visits without a vet. Then open an owner in the frontend.

<details>
<summary>Show solution</summary>

`src/main/resources/graphql/schema.graphqls`

```graphql
type Pet {
    # ...existing fields...
    "The visits of this pet, oldest first"
    visits: [Visit!]!
}

type Visit {
    # ...existing fields...
    "The vet who sees the pet. The Petclinic web pages book visits without a vet."
    vet: Vet
}
```

`vet` may be `null`, because the web pages book visits without a vet. Both fields resolve through getters, so the Java stays as it is, and Hibernate's batch fetching keeps the number of statements low.

</details>

## Recap

- A field whose parent has a matching getter resolves by itself, through the default resolver.
- `@SchemaMapping` resolves a field that the parent cannot answer, one parent at a time.
- One call per parent is the N+1 problem: invisible on a laptop, and slow in production.
- `@BatchMapping` makes one call per request, and the schema stays the same.
- Hibernate's batch fetching does the same for lazy relationships behind getters, as long as open-in-view keeps the persistence context open.

Chapter 3 changes data: mutations, input types, and a `Date` type for the dates.
