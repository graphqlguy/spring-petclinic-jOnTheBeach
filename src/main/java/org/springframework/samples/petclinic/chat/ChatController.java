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
