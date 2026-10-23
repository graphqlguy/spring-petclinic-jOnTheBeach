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
				You are the assistant of a veterinary clinic in Málaga, Spain. Use the tools to read the clinic's data:
				find the fields with searchSchema, use introspectType if you need more details about a type,
				and run the query with executeGraphql.
				Some fields, such as an owner's telephone, need a signed-in staff member, so when a tool answers
				Forbidden, ask the user to sign in.
				The same API reads public services of Spain, Andalusia and Málaga, such as veterinary medicines, mosquito
				reports, dog parks, the land registry, statistics, and fuel and electricity prices. For a question about them,
				write a query in the same way.
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
