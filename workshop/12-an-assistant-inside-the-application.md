# Chapter 12: An assistant inside the application

> **User story** · As a receptionist, I want to ask the clinic's assistant in my own words, so that I can find owners and book visits without clicking through the pages.

In chapters 10 and 11, an AI application outside ours called our tools over MCP. In this chapter, the AI application is ours: a chat in the frontend, answered by a model in Ollama through [Spring AI](https://docs.spring.io/spring-ai/reference/), with chapter 11's operation files as its tools. The tools run inside the application, as the user who asks.

By the end of this chapter, you will:

- Give a Spring AI `ChatClient` the operation files as in-process tools
- Run the chat on a model in Ollama, or on a hosted model
- See a tool call act as the signed-in user
- Check what the assistant did, and give it errors it can act on

## 1. How a question becomes an answer

| Step | What happens |
|---|---|
| 1 | The frontend sends the question to `POST /api/chat`, with the user's token when someone is signed in. |
| 2 | Spring AI's `ChatClient` sends the model a system prompt, the question and the 12 tools. |
| 3 | The model answers, or asks for a tool with its arguments. |
| 4 | Spring AI runs the tool: GATool runs the tool's operation file in our GraphQL engine, as the user who asked. |
| 5 | The model reads the tool's result, then asks for another tool or answers. |

The frontend has had the chat panel since the starting point. It shows the panel once `GET /api/chat` answers `405 Method Not Allowed`, which tells it that the endpoint exists.

## 2. Add Spring AI

Add two starters next to GATool's:

`pom.xml`

```xml
<dependency>
  <groupId>io.gatool</groupId>
  <artifactId>gatool-in-process-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-model-ollama</artifactId>
  <version>2.0.1</version>
</dependency>
```

| Starter | Brings |
|---|---|
| `gatool-in-process-spring-boot-starter` | A `GAToolCallbacks` bean, which hands the tools to a `ChatClient`. These tools stay off the MCP server, which serves its own. |
| `spring-ai-starter-model-ollama` | A `ChatClient.Builder` for a model in Ollama. GATool 0.1.0 is built with Spring AI 2.0.1. |

> [!NOTE]
> With Gradle, add `implementation 'io.gatool:gatool-in-process-spring-boot-starter:0.1.0'` and `implementation 'org.springframework.ai:spring-ai-starter-model-ollama:2.0.1'` to `build.gradle`.

If Ollama cannot run on your laptop, do step 9 now, and from then on start the backend the way it says.

## 3. Configure the model

Add a Chat section below the MCP section:

`src/main/resources/application.properties`

```properties
# Chat
# The assistant in the frontend gets the same operation files as tools, inside the application
gatool.in-process.operations.locations=classpath*:operations/
# The chat model in Ollama, pulled in the setup with: ollama pull granite4.1:8b
spring.ai.ollama.chat.model=granite4.1:8b
# The model reads the tools, the question and the tool results within this many tokens.
# Ollama picks a size from your memory when this is unset, so set it for the same result on every laptop.
spring.ai.ollama.chat.num-ctx=8192
# One call to the model, so the chat reports at once when Ollama is not running. By default
# Spring AI tries a failed call 10 more times, with pauses that grow to 3 minutes.
spring.ai.retry.max-attempts=0
# The calls to the model go through the JDK's HTTP client. Reactor Netty, which Spring AI also
# brings, logs an ERROR about DNS on macOS at its first call.
spring.http.clients.imperative.factory=jdk
# Logs the name of each tool the model calls
logging.level.org.springframework.ai.model.tool=DEBUG
```

| Property | Meaning |
|---|---|
| `gatool.in-process.operations.locations` | The operation files of the in-process tools. It is separate from the MCP server's setting, so the application's own assistant can get other tools. |
| `spring.ai.ollama.chat.model` | The model from the setup. Ollama must be running: `ollama list` answers when it is. |
| `spring.ai.ollama.chat.num-ctx` | Everything the model reads for one answer must fit in this many tokens. A booking takes up to about 2,800. On a laptop with 128 GB, Ollama gave the model 131,072 tokens and 27 GB of memory; with 8192, it takes 6.9 GB. |

## 4. The chat endpoint

**Create a new class** named `ChatController` in a new package, `org.springframework.samples.petclinic.chat`:

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java`

```java
package org.springframework.samples.petclinic.chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools) {
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider()).build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question) {
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read and change the clinic's data.
				Changing data needs a signed-in staff member, so when a tool answers Unauthorized, ask the user to sign in.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
		return chatClient.prompt().system(system).user(question).call().content();
	}

	// The chat panel shows this text, for example when Ollama is not running
	@ExceptionHandler
	ResponseEntity<String> modelFailed(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The model did not answer: " + ex.getMessage());
	}

}
```

| Element | Meaning |
|---|---|
| `ChatClient.Builder` | Spring AI's builder, set up for the model of step 3. |
| `defaultTools(tools.toolCallbackProvider())` | Gives every prompt of this client the 12 in-process tools. |
| The system prompt | What the model needs and cannot know: its role, what to do when a tool refuses, and today's date, which goes with every question. |
| `call().content()` | Sends the prompt, runs every tool the model asks for, and returns the model's last answer as text. |
| `@PostMapping` | The frontend sends the question as plain text. A `GET` answers `405`. |
| `@ExceptionHandler` | When the model cannot be reached, the chat shows one line, such as `The model did not answer: I/O error on POST request for "http://localhost:11434/api/chat": Connection refused`. |

## 5. Ask the assistant

Restart. After the MCP tools, GATool lists the in-process tools:

```text
GATool serves 12 in-process tools:
  addOwner from file:.../target/classes/operations/mutations/addOwner.graphql (about 152 tokens)
  addPet from file:.../target/classes/operations/mutations/addPet.graphql (about 231 tokens)
  addVisit from file:.../target/classes/operations/mutations/addVisit.graphql (about 279 tokens)
  updateOwner from file:.../target/classes/operations/mutations/updateOwner.graphql (about 160 tokens)
  updatePet from file:.../target/classes/operations/mutations/updatePet.graphql (about 168 tokens)
  findOwners from file:.../target/classes/operations/queries/findOwners.graphql (about 310 tokens)
  findVisits from file:.../target/classes/operations/queries/findVisits.graphql (about 155 tokens)
  getOwnerDetails from file:.../target/classes/operations/queries/getOwnerDetails.graphql (about 103 tokens)
  getVetDetails from file:.../target/classes/operations/queries/getVetDetails.graphql (about 100 tokens)
  listPetTypes from file:.../target/classes/operations/queries/listPetTypes.graphql (about 65 tokens)
  listVets from file:.../target/classes/operations/queries/listVets.graphql (about 78 tokens)
  search from file:.../target/classes/operations/queries/search.graphql (about 106 tokens)
  about 1907 tokens in all, which every agent that lists these tools reads before it calls one
```

Refresh the frontend. The home page lists *Chapter 12 Clinic assistant*, and every page has an **Ask the assistant** button at the bottom right. Open it and ask:

```text
Which vets work at the clinic, and what are their specialties?
```

The first answer takes longer, while Ollama loads the model. The backend's log shows the tool the model chose:

```text
DEBUG ... o.s.a.m.tool.DefaultToolCallingManager   : Executing tool call: listVets
```

The answer is a table of the six vets. Models write Markdown, and the panel renders it.

## 6. The tools act as the user who asks

Without signing in, ask:

```text
Book an x-ray for Perla, the pet of Pablo Morales, with Helen Leary tomorrow.
```

The log shows which tools the model called, and they vary from run to run: one of our runs called `findOwners`, `getOwnerDetails`, `listVets` and `addVisit`. All 5 of our runs ended with `addVisit`, which answered `Unauthorized`, and the assistant asks you to sign in:

> I need you to sign in so that I can book the x-ray for Perla with Helen Leary for tomorrow.

Sign in as `staff` / `staff` and ask again. A notification for the visit appears at the top right, and the assistant answers:

> The x-ray for Perla, owned by Pablo Morales, has been booked with Helen Leary for tomorrow, 2026-10-23. Visit ID: 33.

| Part | What it does |
|---|---|
| The chat panel | Sends the signed-in user's token in the `Authorization` header, as the pages do for `/graphql`. |
| Spring Security | Checks the token and puts the user on the request's thread, as in chapter 5. |
| Spring AI | Runs each tool on that same thread, inside `call()`. |
| GATool | Runs the operation with that thread's security context, so the `@PreAuthorize` on `addVisit` decides. |

GATool says so in a warning at startup:

```text
A tool call in embedded mode reaches the GraphQL engine directly, so every servlet filter and every authorizeHttpRequests rule on the GraphQL path stays out of it. The security context travels with the call, so method security on a resolver still decides, which is where authorization belongs for this mode.
```

Our rules have been on the controllers since chapter 5, so the assistant can do what the user who asks can do.

## 7. Check what the assistant did

Signed in as staff, ask:

```text
Book a check-up for Perla, the pet of Pablo Morales, with Helen Leary next Wednesday.
```

Then open the **Visits** page and look for the date. These are mistakes `granite4.1:8b` made in our test runs on Thursday 2026-10-22:

| Asked | What it did |
|---|---|
| "next Wednesday" | Booked Sunday 2026-10-25, and answered "next Wednesday, 2026-10-25" |
| "next Monday" | Booked a wrong date in all five runs, from 2026-10-23 to 2026-10-30, where Monday is 2026-10-26 |
| "next week" | Answered that next week runs from October 25 to November 1 |
| "with Helen Leary" | Left the vet out of 2 bookings in 6, and answered "with Helen Leary" each time |
| "tomorrow" | Booked the right day every time |

The system prompt gives the model today's date, and turning a weekday into a date is arithmetic, which a model of this size gets wrong more often than right. "Tomorrow" works because `addVisit`'s description says *Leave the date out to book the visit for tomorrow*, and the model follows it. Every booking shows a notification as it happens, so an assistant's changes are there to check, whichever model makes them. The hosted models of step 9 are larger: try the same questions there.

## 8. Errors the model can act on

The model sometimes passes a name where an id belongs. In our runs, it called `addVisit` with `petId: "pet_1"` and `vetId: "vet_2"`, and `getOwnerDetails` with `id: "pablo_morales"`. Send the second one in GraphiQL:

```graphql
query {
  owner(id: "pablo_morales") {
    lastName
  }
}
```

```json
{
  "errors": [
    {
      "message": "INTERNAL_ERROR for e122b4ae-7906-176b-0407-ba41b75165a3",
      "path": ["owner"],
      "extensions": { "classification": "INTERNAL_ERROR" }
    }
  ],
  "data": { "owner": null }
}
```

GraphQL's `ID` type accepts any text, so the request reaches `OwnerGraphQLController`, whose `Integer` parameter cannot take `pablo_morales`. The log says `Unresolved BindException`. Given `INTERNAL_ERROR`, the model in our runs told the user that the clinic's system was having internal errors.

Add a handler to the class from chapter 4, after `invalidInput`:

`src/main/java/org/springframework/samples/petclinic/system/GraphQLExceptionHandlers.java`

```java
	@GraphQlExceptionHandler
	List<GraphQLError> notAnId(BindException ex, DataFetchingEnvironment environment) {
		return ex.getFieldErrors()
			.stream()
			.map((error) -> GraphqlErrorBuilder.newError(environment)
				.errorType(ErrorType.BAD_REQUEST)
				.message("'" + error.getRejectedValue()
						+ "' is not an id. An id is a number, which queries such as search, owners and vets return.")
				.build())
			.toList();
	}
```

and its import:

```java
import org.springframework.validation.BindException;
```

Restart, and send the query again:

```json
{
  "errors": [
    {
      "message": "'pablo_morales' is not an id. An id is a number, which queries such as search, owners and vets return.",
      "path": ["owner"],
      "extensions": { "classification": "BAD_REQUEST" }
    }
  ],
  "data": { "owner": null }
}
```

`BindException` holds one error for each value that could not be converted, so `addVisit` with `pet_1` and `vet_2` gets two. With this answer, the model in our runs looked the ids up with `search` and booked the visit. The message says where ids come from, as chapter 9's descriptions do, and MCP clients get the same error.

## 9. Use a hosted model instead of Ollama

Spring AI has a starter for each provider. A Maven profile adds one, and runs the application with the Spring profile of the same name. Add three profiles at the top of the `<profiles>` section:

`pom.xml`

```xml
<!-- A hosted chat model in place of Ollama, for example: ./mvnw spring-boot:run -Pmistral
     Each profile adds the model's starter, and runs the application with the Spring
     profile of the same name, which application-mistral.properties configures. -->
<profile>
  <id>mistral</id>
  <properties>
    <spring-boot.run.profiles>mistral</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-mistral-ai</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
<profile>
  <id>openai</id>
  <properties>
    <spring-boot.run.profiles>openai</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-openai</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
<profile>
  <id>anthropic</id>
  <properties>
    <spring-boot.run.profiles>anthropic</spring-boot.run.profiles>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-anthropic</artifactId>
      <version>2.0.1</version>
    </dependency>
  </dependencies>
</profile>
```

Then create the properties file for your provider next to `application.properties`:

`src/main/resources/application-mistral.properties`

```properties
# ./mvnw spring-boot:run -Pmistral, with your key in the MISTRAL_API_KEY environment variable
spring.ai.model.chat=mistral
spring.ai.mistralai.api-key=${MISTRAL_API_KEY}
spring.ai.mistralai.chat.model=mistral-small-latest
```

`src/main/resources/application-openai.properties`

```properties
# ./mvnw spring-boot:run -Popenai, with your key in the OPENAI_API_KEY environment variable
spring.ai.model.chat=openai
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.model=gpt-5-mini
```

`src/main/resources/application-anthropic.properties`

```properties
# ./mvnw spring-boot:run -Panthropic, with your key in the ANTHROPIC_API_KEY environment variable
spring.ai.model.chat=anthropic
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.chat.model=claude-haiku-5-5
```

The Ollama starter stays in the project, so `spring.ai.model.chat` says which provider's model the `ChatClient` gets.

| Provider | Get a key at | Cost |
|---|---|---|
| Mistral | https://console.mistral.ai/api-keys: create an account and choose Free mode, then **Create new key**. Mistral shows the key once. | Free mode works without a credit card, with low rate limits. |
| OpenAI | https://platform.openai.com/api-keys | Paid |
| Anthropic | https://platform.claude.com/settings/keys | Paid |

Start the backend with the key in an environment variable:

```bash
export MISTRAL_API_KEY=your-key
./mvnw spring-boot:run -Pmistral
```

On Windows, set the key with `set MISTRAL_API_KEY=your-key` in Command Prompt or `$env:MISTRAL_API_KEY="your-key"` in PowerShell, then run `mvnw.cmd spring-boot:run -Pmistral`. The log says `The following 1 profile is active: "mistral"`. A wrong or missing key shows in the chat as `The model did not answer: HTTP 401 - {"detail":"Invalid API Key"}`. Keep the `-P` flag for the rest of the workshop.

## Exercise: a conversation

Signed in as staff, ask one question after the other:

```text
Which pets does Pablo Morales have?
```

```text
Book an x-ray for the cat with Helen Leary tomorrow.
```

The second answer shows that the model has lost the first: *I'm unable to locate an owner named Helen Leary in the clinic's records.* Each call sends the model one question. Give the assistant the conversation so far.

- Spring AI configures a `ChatMemory` bean, which keeps the last 20 messages of each conversation in memory.
- A `MessageChatMemoryAdvisor` adds a conversation's messages to each prompt, and stores the new ones.
- The frontend sends the session cookie with each question, so the HTTP session can name the conversation.

<details>
<summary>Show solution</summary>

`src/main/java/org/springframework/samples/petclinic/chat/ChatController.java`

```java
package org.springframework.samples.petclinic.chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.gatool.boot.inprocess.GAToolCallbacks;
import jakarta.servlet.http.HttpSession;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH);

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder, GAToolCallbacks tools, ChatMemory memory) {
		this.chatClient = builder.defaultTools(tools.toolCallbackProvider())
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
			.build();
	}

	@PostMapping("/api/chat")
	String chat(@RequestBody String question, HttpSession session) {
		String system = """
				You are the assistant of a veterinary clinic. Use the tools to read and change the clinic's data.
				Changing data needs a signed-in staff member, so when a tool answers Unauthorized, ask the user to sign in.
				Today is %s. Answer briefly."""
			.formatted(LocalDate.now().format(DAY));
		return chatClient.prompt()
			.system(system)
			.user(question)
			.advisors((advisor) -> advisor.param(ChatMemory.CONVERSATION_ID, session.getId()))
			.call()
			.content();
	}

	// The chat panel shows this text, for example when Ollama is not running
	@ExceptionHandler
	ResponseEntity<String> modelFailed(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The model did not answer: " + ex.getMessage());
	}

}
```

Restart and ask the two questions again:

> An x-ray visit has been scheduled for Perla (Pablo Morales's cat) with Helen Leary for tomorrow, 2026-10-23. The visit ID is 33.

The memory lives in the backend, one conversation for each session, and a restart empties it. In one of our four runs, the model booked another owner's cat, so check the notification here too.

</details>

## Recap

- `gatool.in-process.operations.locations` gives the application's own `ChatClient` the operation files as tools, through the `GAToolCallbacks` bean.
- A tool call runs on the request's thread, so it acts as the signed-in user, and `@PreAuthorize` decides what the assistant may change.
- The model knows only what the prompt tells it, today's date included, and a small model gets dates and names wrong often enough that its changes need checking.
- An error message that says how to correct the call lets the model try again.
- A Maven profile switches the chat to Mistral, OpenAI or Anthropic.
- `ChatMemory` and `MessageChatMemoryAdvisor` give the model the conversation so far.

Chapter 13 secures the MCP endpoint, with an authorization server inside the application.
