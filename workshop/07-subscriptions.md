# Chapter 7: Subscriptions

> **User story** · As a member of staff, I want a notification for each new booking, so that I see it without reloading the page.

Queries and mutations answer once. A **subscription** keeps the connection open, and the server sends an event each time something happens. In this chapter, every new visit goes out to the clients that listen.

By the end of this chapter, you will:

- Add the third root type, `Subscription`
- Serve subscriptions over a WebSocket, and over Server-Sent Events
- Turn application events into a stream with Reactor

## 1. Add a WebSocket transport

A subscription needs a connection that stays open. Spring for GraphQL serves subscriptions over WebSockets, with the `graphql-transport-ws` protocol. Add the starter:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

> [!NOTE]
> With Gradle, add `implementation 'org.springframework.boot:spring-boot-starter-websocket'` to `build.gradle`.

Then give the WebSocket endpoint the same path as the HTTP one. Add it below `spring.graphql.graphiql.enabled`:

`src/main/resources/application.properties`

```properties
# Subscriptions run over a WebSocket at the same path as the HTTP endpoint
spring.graphql.websocket.path=/graphql
```

## 2. The schema

Add the third root type, next to `Mutation`:

`src/main/resources/graphql/schema.graphqls`

```graphql
"Events a client can listen to, over a WebSocket or Server-Sent Events"
type Subscription {
    "Each visit as it is booked"
    visitBooked: Visit!
}
```

A subscription field returns one event at a time: here, one `Visit` per booking.

## 3. From an event to a stream

`VisitService.addVisit` publishes a `VisitBookedEvent`, with the new visit's id, each time it books a visit. The subscription turns those events into a stream. Add a sink, a listener and the subscription to `VisitGraphQLController`:

`src/main/java/org/springframework/samples/petclinic/owner/visit/VisitGraphQLController.java`

```java
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.transaction.event.TransactionalEventListener;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

private final Sinks.Many<Visit> bookedVisits = Sinks.many().multicast().directBestEffort();

@TransactionalEventListener
void onVisitBooked(VisitBookedEvent event) {
	bookedVisits.tryEmitNext(visitService.findWithDetails(event.visitId()));
}

@SubscriptionMapping
Flux<Visit> visitBooked() {
	return bookedVisits.asFlux();
}
```

Put the field with the other field, below `visitService`, and the two methods at the end of the class.

| Element | Meaning |
|---|---|
| `Sinks.many().multicast()` | A Reactor sink: code pushes values in, and every current subscriber receives each one. |
| `directBestEffort()` | A subscriber that cannot keep up misses events, and the others still receive theirs. |
| `@TransactionalEventListener` | Runs after the booking's transaction commits, so only a booking that succeeded is announced. |
| `findWithDetails` | Reads the visit with its pet, owner and vet in one statement. The event goes out on the WebSocket's thread, after the HTTP request that booked the visit has finished, so everything the subscriber may ask for has to be loaded already. |
| `@SubscriptionMapping` | Binds the method to the `Subscription` field. It returns a `Flux`, a stream of events. |

Restart. The startup log now lists both transports:

```text
GraphQL endpoint HTTP POST /graphql
GraphQL endpoint WebSocket /graphql
```

## 4. Watch the bookings arrive

Refresh the frontend. From now on, every page shows a notification for each visit as it is booked, for ten seconds. The frontend subscribes with the operation in `src/main/resources/operations-ui/visitBooked.graphql`, over the WebSocket.

Book a visit from GraphiQL, in another tab. Booking needs a staff token, as in chapter 5, so sign in first:

```graphql
mutation {
  login(username: "staff", password: "staff")
}
```

Copy the token from the answer, open the **Headers** tab below the editor, and add it:

```json
{ "Authorization": "Bearer eyJhbGciOiJSUzI1NiJ9..." }
```

Then book a visit for Simba, Lucía Navarro's cat:

```graphql
mutation {
  addVisit(input: { petId: 16, date: "2026-11-06", description: "flea treatment" }) {
    id
  }
}
```

Switch to the frontend's tab: a notification for the visit shows at the top right.

Spring for GraphQL also serves subscriptions over Server-Sent Events: one HTTP response that stays open and carries one event after another. On macOS, Linux or Git Bash, listen with `curl`:

```bash
curl -N http://localhost:8080/graphql \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"query":"subscription { visitBooked { date description pet { name } } }"}'
```

Run the `addVisit` mutation in GraphiQL again, and the event arrives:

```text
event:next
data:{"data":{"visitBooked":{"date":"2026-11-06","description":"flea treatment","pet":{"name":"Simba"}}}}
```

> [!NOTE]
> GraphiQL, as Spring for GraphQL serves it, sends a subscription as an ordinary HTTP request, and the answer is the stream object itself: `{"data":{"upstreamPublisher":...}}`. Use the frontend or `curl` for subscriptions.

A visit booked through the Petclinic web pages at port 8080 does not appear. Those pages save visits through the owner repository, and only `VisitService` publishes the event.

## Recap

- A subscription returns a stream of events over a connection that stays open.
- Spring for GraphQL serves it over a WebSocket, and over Server-Sent Events.
- A `@SubscriptionMapping` method returns a `Flux`, and a Reactor sink turns application events into one.
- An event that leaves the request's thread needs its data loaded first.

Chapter 8 tests the API the way a client uses it.
