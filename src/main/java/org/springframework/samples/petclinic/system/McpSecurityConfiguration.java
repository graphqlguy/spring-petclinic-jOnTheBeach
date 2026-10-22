package org.springframework.samples.petclinic.system;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.gatool.boot.mcp.security.McpServerSecurityConfigurer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
class McpSecurityConfiguration {

	private static final Set<String> MCP_SCOPES = Set.of("mcp:tools", "petclinic:write");

	// The MCP endpoint and its metadata, ahead of every other chain
	@Bean
	@Order(1)
	SecurityFilterChain mcpEndpoint(HttpSecurity http) throws Exception {
		return http.securityMatcher("/mcp", "/.well-known/oauth-protected-resource/mcp")
			.with(McpServerSecurityConfigurer.mcpServer(), withDefaults())
			.build();
	}

	// The authorization server's endpoints. A browser that is not signed in goes to the
	// sign-in page.
	@Bean
	@Order(2)
	SecurityFilterChain authorizationServer(HttpSecurity http) throws Exception {
		http.oauth2AuthorizationServer((server) -> {
			http.securityMatcher(server.getEndpointsMatcher());
			server.clientRegistrationEndpoint((registration) -> registration.openRegistrationAllowed(true)
				.authenticationProviders((providers) -> providers.forEach(McpSecurityConfiguration::mcpClients)));
		})
			.authorizeHttpRequests(
					(requests) -> requests.requestMatchers("/oauth2/register").permitAll().anyRequest().authenticated())
			.exceptionHandling((exceptions) -> exceptions.defaultAuthenticationEntryPointFor(
					new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
		return http.build();
	}

	// An MCP client registers itself and asks for scopes. Spring Security refuses
	// every scope at open registration until it is told which ones to accept.
	// The client's tokens last 8 hours, so an MCP client stays signed in while you
	// restart the application.
	private static void mcpClients(AuthenticationProvider provider) {
		if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider registration) {
			registration.setAuthenticationValidator(
					OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
						.andThen(OAuth2ClientRegistrationAuthenticationValidator.DEFAULT_JWK_SET_URI_VALIDATOR)
						.andThen((context) -> {
							OAuth2ClientRegistrationAuthenticationToken request = context.getAuthentication();
							List<String> scopes = request.getClientRegistration().getScopes();
							if (scopes != null && !MCP_SCOPES.containsAll(scopes)) {
								throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
							}
						}));
			OAuth2ClientRegistrationRegisteredClientConverter converter = new OAuth2ClientRegistrationRegisteredClientConverter();
			registration.setRegisteredClientConverter((request) -> RegisteredClient.from(converter.convert(request))
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(8)).build())
				.build());
		}
	}

	// The clients that registered themselves, until the application stops
	@Bean
	RegisteredClientRepository registeredClients() {
		Map<String, RegisteredClient> clients = new ConcurrentHashMap<>();
		return new RegisteredClientRepository() {

			@Override
			public void save(RegisteredClient client) {
				clients.put(client.getId(), client);
			}

			@Override
			public RegisteredClient findById(String id) {
				return clients.get(id);
			}

			@Override
			public RegisteredClient findByClientId(String clientId) {
				return clients.values()
					.stream()
					.filter((c) -> c.getClientId().equals(clientId))
					.findFirst()
					.orElse(null);
			}

		};
	}

	// Every access token names the MCP endpoint as its audience, and carries
	// petclinic:write for staff alone, as the tokens of the login mutation do
	@Bean
	OAuth2TokenCustomizer<JwtEncodingContext> accessTokens(@Value("${gatool.mcp.security.resource}") String resource) {
		return (context) -> {
			if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
				return;
			}
			boolean staff = context.getPrincipal()
				.getAuthorities()
				.stream()
				.anyMatch((authority) -> authority.getAuthority().equals("ROLE_STAFF"));
			Set<String> scopes = new HashSet<>(context.getAuthorizedScopes());
			if (staff) {
				scopes.add("petclinic:write");
			}
			else {
				scopes.remove("petclinic:write");
			}
			context.getClaims().audience(List.of(resource)).claim("scope", scopes);
		};
	}

}
