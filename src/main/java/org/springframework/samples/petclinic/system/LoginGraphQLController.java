package org.springframework.samples.petclinic.system;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class LoginGraphQLController {

	private final LoginService loginService;

	LoginGraphQLController(LoginService loginService) {
		this.loginService = loginService;
	}

	@MutationMapping
	String login(@Argument String username, @Argument String password) {
		return loginService.login(username, password);
	}

}
