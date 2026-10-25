# Chapter 13: Securing the MCP endpoint

> **User story** · As the clinic manager, I want every AI client to sign in before it uses our tools, so that an agent changes the clinic's data only for a member of staff.

Since chapter 10, `/mcp` has answered every caller, because `allow-mcp-calls-without-authentication` is on. MCP secures an HTTP server with OAuth 2.1: the MCP server checks access tokens, an **authorization server** signs users in and issues the tokens, and a client finds the authorization server by asking the MCP server. In this chapter, the application becomes its own authorization server, with the one in Spring Security, and the MCP Inspector signs in through it.

By the end of this chapter, you will:

- Know the steps an MCP client takes to sign in
- Run an OAuth 2.1 authorization server inside the application
- Accept only the tokens issued for the MCP endpoint
- Sign the MCP Inspector in as staff and as guest

## 1. How an MCP client signs in

| Step | The client | Served by |
|---|---|---|
| 1 | Calls `/mcp` without a token, and gets `401` with a `WWW-Authenticate` header that names the endpoint's metadata | GATool |
| 2 | Reads the protected resource metadata, which names the authorization server and the scopes to ask for | GATool |
| 3 | Reads the authorization server's metadata, which lists its endpoints | Authorization server |
| 4 | Registers itself at `/oauth2/register` and gets a client id. This is Dynamic Client Registration. | Authorization server |
| 5 | Opens the browser at `/oauth2/authorize`, where you sign in and approve the scopes | Authorization server |
| 6 | Exchanges the code it got back for an access token at `/oauth2/token`, and proves with PKCE that it started step 5 | Authorization server |
| 7 | Calls `/mcp` with `Authorization: Bearer <token>` | GATool, then our resolvers |

The token names `/mcp` as its **audience**. MCP requires a server to accept only the tokens issued for it, so a token that another server received cannot be replayed here.

## 2. Add the authorization server

Add the starter next to the resource server starter:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-oauth2-authorization-server</artifactId>
</dependency>
```

> [!NOTE]
> With Gradle, add `implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server'` to `build.gradle`.

## 3. Close the endpoint

Replace the Security and MCP sections:

`src/main/resources/application.properties`

```properties
# Security
# The key pair that signs and checks access tokens. For development only.
petclinic.jwt.public-key=classpath:keys/petclinic-dev.pub
petclinic.jwt.private-key=classpath:keys/petclinic-dev.key

# MCP
# The name MCP clients show for this server
spring.ai.mcp.server.name=petclinic
# GATool serves the frontend's operations as MCP tools at /mcp, one tool for each file
gatool.mcp.operations.locations=classpath*:operations/
# An answer with data and errors is a success: the errors say which fields stay null, and why
gatool.results.partial-results-as-success=true
# The MCP endpoint is an OAuth 2.1 resource server: every call carries an access token that
# the authorization server below issued for the endpoint
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:${server.port:8080}
gatool.mcp.security.resource=http://localhost:${server.port:8080}/mcp
# Every MCP call needs this scope
gatool.mcp.security.baseline-scopes=mcp:tools

# Authorization server
# This application signs MCP clients in, and issues their tokens
spring.security.oauth2.authorizationserver.issuer=http://localhost:${server.port:8080}
```

| Property | Meaning |
|---|---|
| `spring.ai.mcp.server.name` | The name the Inspector shows for the server. |
| `issuer-uri` | Who issues the tokens: this application. A token has to carry it as `iss`, and the protected resource metadata names it as the authorization server. |
| `gatool.mcp.security.resource` | The endpoint's URL, which a token has to carry as `aud`. |
| `baseline-scopes` | The scope that every MCP call needs. |
| `authorizationserver.issuer` | The name the authorization server writes into its tokens and its metadata. |
| `${server.port:8080}` | Follows the port, if you changed it. |

The unsafe switch is gone, so GATool secures the endpoint. Chapter 5's `public-key-location` line is gone too: the decoder of step 5 reads the key itself.

## 4. The authorization server and the MCP endpoint

**Create a new class** named `McpSecurityConfiguration` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/McpSecurityConfiguration.java`

```java
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
```

| Element | Meaning |
|---|---|
| `mcpEndpoint` | A filter chain for `/mcp` and its metadata, ordered first. GATool's configurer asks every call for a token with `mcp:tools`, answers `401` and `403` the way MCP clients expect, and serves the metadata. |
| `authorizationServer` | A filter chain for the authorization server's endpoints, ordered second. `openRegistrationAllowed(true)` lets a client register without credentials, as an MCP client does on its first visit. A browser that is not signed in goes to `/login`. |
| `mcpClients` | Accepts a registration that asks for our two scopes, and refuses any other scope. It also gives each client tokens that last 8 hours, where the default is 5 minutes. |
| `registeredClients` | Keeps the registered clients in a map, so a restart forgets them. The note in step 9 says what that means for the Inspector. |
| `accessTokens` | Writes the audience into every access token, and gives `petclinic:write`, the scope that chapter 5's `@PreAuthorize` rules check, to staff alone. |

Chapter 5's chain matches every request. A chain without `@Order` sorts last, so it comes after these two.

## 5. The keys, the sign-in page and the decoder

Replace the whole class:

`src/main/java/org/springframework/samples/petclinic/system/SecurityConfiguration.java`

```java
package org.springframework.samples.petclinic.system;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.oauth2ResourceServer((resourceServer) -> resourceServer.jwt(Customizer.withDefaults()))
			// The sign-in page of the authorization server
			.formLogin(Customizer.withDefaults())
			.csrf((csrf) -> csrf.disable())
			.build();
	}

	@Bean
	UserDetailsService users() {
		return new InMemoryUserDetailsManager(
				User.withUsername("staff").password("{noop}staff").roles("STAFF").build(),
				User.withUsername("guest").password("{noop}guest").roles("GUEST").build());
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	// The development key pair, which signs the tokens of the login mutation and of the
	// authorization server, and which the authorization server publishes at /oauth2/jwks
	@Bean
	JWKSource<SecurityContext> jwkSource(@Value("${petclinic.jwt.public-key}") RSAPublicKey publicKey,
			@Value("${petclinic.jwt.private-key}") RSAPrivateKey privateKey) {
		RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("petclinic-dev").build();
		return new ImmutableJWKSet<>(new JWKSet(key));
	}

	@Bean
	JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
		return new NimbusJwtEncoder(jwkSource);
	}

	// Accepts a token signed with the development key, from this issuer, for this server
	@Bean
	JwtDecoder jwtDecoder(@Value("${petclinic.jwt.public-key}") RSAPublicKey publicKey,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${gatool.mcp.security.resource}") String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(issuer),
				new JwtAudienceValidator(audience)));
		return decoder;
	}

}
```

| Element | Meaning |
|---|---|
| `formLogin` | The sign-in page at `/login`, where the authorization server sends you. |
| `jwkSource` | Chapter 5's development key pair. The authorization server signs its tokens with it, and publishes the public half. |
| `jwtEncoder` | Signs the login mutation's tokens with the same key, as before. |
| `jwtDecoder` | Accepts a token signed with the key, issued by `issuer-uri`, for the audience `/mcp`. With `issuer-uri` set, Spring Boot's own decoder would download the keys from that address at the first token, and the tests run the application on a random port. |

## 6. The login mutation's tokens

The decoder now checks the issuer and the audience of every token, the frontend's included. Replace the whole class:

`src/main/java/org/springframework/samples/petclinic/system/LoginGraphQlController.java`

```java
package org.springframework.samples.petclinic.system;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Controller;

@Controller
class LoginGraphQlController {

	private final UserDetailsService users;

	private final PasswordEncoder passwordEncoder;

	private final JwtEncoder jwtEncoder;

	private final String issuer;

	private final String audience;

	LoginGraphQlController(UserDetailsService users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${gatool.mcp.security.resource}") String audience) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.audience = audience;
	}

	@MutationMapping
	String login(@Argument String username, @Argument String password) {
		UserDetails user = this.users.loadUserByUsername(username);
		if (!this.passwordEncoder.matches(password, user.getPassword())) {
			throw new BadCredentialsException("Wrong username or password");
		}
		boolean staff = user.getAuthorities().stream().anyMatch((a) -> a.getAuthority().equals("ROLE_STAFF"));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.issuer)
			.audience(List.of(this.audience))
			.subject(user.getUsername())
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofHours(8)))
			.claim("scope", staff ? "petclinic:write" : "")
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
```

The issuer and the audience come from the properties, and the scope stays as it was. Without `mcp:tools`, the frontend's token works on `/graphql` and `/api/chat`, and `/mcp` refuses it.

## 7. Restart, and follow the first steps by hand

Restart. GATool reminds us of a check it leaves to our decoder:

```text
GATool leaves the audience check to this application's own JwtDecoder bean, because Spring Boot's decoder, which reads spring.security.oauth2.resourceserver.jwt.audiences, backs off for it. MCP requires that check, so make sure that bean validates the aud claim.
```

Our decoder makes it, with `JwtAudienceValidator`. Call the endpoint without a token, as a client does in step 1:

```bash
curl -s -i http://localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Among the headers of the answer:

```text
HTTP/1.1 401
WWW-Authenticate: Bearer scope="mcp:tools", resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource/mcp"
```

Read the document it names, as in step 2:

```bash
curl -s http://localhost:8080/.well-known/oauth-protected-resource/mcp
```

```json
{
  "resource": "http://localhost:8080/mcp",
  "bearer_methods_supported": ["header"],
  "authorization_servers": ["http://localhost:8080"],
  "scopes_supported": ["mcp:tools"],
  "resource_name": "petclinic"
}
```

And the authorization server's metadata, as in step 3. Among its fields:

```bash
curl -s http://localhost:8080/.well-known/oauth-authorization-server
```

```json
{
  "issuer": "http://localhost:8080",
  "authorization_endpoint": "http://localhost:8080/oauth2/authorize",
  "token_endpoint": "http://localhost:8080/oauth2/token",
  "registration_endpoint": "http://localhost:8080/oauth2/register",
  "code_challenge_methods_supported": ["S256"]
}
```

## 8. Sign in with the MCP Inspector

If the Inspector still runs with chapter 10's `--header`, stop it with `Ctrl+C`. Start it without a token:

```bash
npx -y @modelcontextprotocol/inspector@2.9.0 --web --transport http --server-url http://localhost:8080/mcp
```

1. Flip the switch on the card. The tab goes to http://localhost:8080/login: the Inspector took steps 1 to 4 on its own, and opened step 5.
2. Sign in as `staff` / `staff`.
3. The consent page reads *&lt;client id&gt; wants to access your account staff* and lists `mcp:tools`. Tick `mcp:tools`, then click **Submit Consent**. A consent with the box left empty approves an empty list of scopes, and the Inspector reports `access_denied`.
4. The tab returns to the Inspector, which says *Authorization complete*, and the card says **Connected**.

On the **Servers** tab, open **Connection Info**. Its **OAuth Details** say *Client registration: Dynamic (DCR)*, *Scopes: mcp:tools*, and show the client id our server issued. Click **Decode JWT**:

```json
"payload": {
  "sub": "staff",
  "aud": "http://localhost:8080/mcp",
  "nbf": 1792675545,
  "scope": ["mcp:tools", "petclinic:write"],
  "iss": "http://localhost:8080",
  "exp": 1792704345,
  "iat": 1792675545,
  "jti": "d59fe28d-b711-4f82-94bf-1ec1c77e798b"
}
```

The Inspector lists the scope it asked for, and the token carries `petclinic:write` too, because `accessTokens` adds it for staff. On the **Tools** tab, run `addVisit` with:

```json
{"petId": 17, "vetId": 2, "description": "x-ray"}
```

```json
{
  "data": {
    "addVisit": { "id": "33", "date": "2026-10-23", "description": "x-ray" }
  }
}
```

## 9. Sign in as guest

In **Connection Info**, click **Clear OAuth state and disconnect**. Flip the switch, sign in as `guest` / `guest`, tick `mcp:tools` and submit. Run `addVisit` again. The Inspector shows a **Tool Error**:

```json
{"data":null,"errors":[{"message":"Forbidden","locations":[{"line":2,"column":3}],"path":["addVisit"],"extensions":{"classification":"FORBIDDEN"}}]}
```

**Decode JWT** shows `"sub": "guest"` and `"scope": ["mcp:tools"]`. The guest can use every tool, and chapter 5's `@PreAuthorize` refuses the write, as it does in the frontend.

> [!NOTE]
> The Inspector keeps its token, which lasts 8 hours and is signed with the development key, so it reconnects after you restart the backend. The registered clients live in memory, so a sign-in after a restart can stop at an error page that says *[invalid_request] OAuth 2.0 Parameter: client_id*: the Inspector used a client id that the backend has forgotten. While the Inspector is connected, **Clear OAuth state and disconnect** makes it register again. Otherwise stop the Inspector, delete `~/.mcp-inspector/storage/oauth.json` (on Windows, `%USERPROFILE%\.mcp-inspector\storage\oauth.json`), and start it again.

The frontend still holds a token from before this chapter, with the old issuer. The backend answers it with `401` and `The iss claim is not valid`, and the frontend signs you out, so sign in again.

## Exercise: call the endpoint yourself

In **Connection Info**, copy the access token with the copy button next to it. Call `listVets` with curl and that token. Then get a token from the login mutation, make the same call with it, and explain the difference.

<details>
<summary>Show solution</summary>

```bash
TOKEN=<paste the token>
curl -s http://localhost:8080/mcp -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"listVets","arguments":{}}}'
```

```json
{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"{\"data\":{\"vets\":[{\"id\":\"1\",\"firstName\":\"James\",\"lastName\":\"Carter\",\"specialties\":[]},..."}]}}
```

```bash
LOGIN=$(curl -s http://localhost:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"mutation { login(username: \"staff\", password: \"staff\") }"}' | sed -E 's/.*"login":"([^"]+)".*/\1/')
curl -s -i http://localhost:8080/mcp -H "Authorization: Bearer $LOGIN" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"listVets","arguments":{}}}'
```

```text
HTTP/1.1 403
WWW-Authenticate: Bearer error="insufficient_scope", error_description="This endpoint needs mcp:tools. The token does not hold any of them.", scope="mcp:tools", resource_metadata="http://localhost:8080/.well-known/oauth-protected-resource/mcp"
```

Both tokens are signed with the same key, by the same issuer, for the same audience, so the decoder accepts both. The login mutation's token lacks `mcp:tools`, and GATool's chain answers `403` with the scope to ask for. The Inspector's token works on `/graphql` as well, and a staff member's returns the telephone of `owner(id: 12)`, since it carries `petclinic:write`.

</details>

## Recap

- An MCP client finds the authorization server through the MCP server's `401` and its protected resource metadata, registers itself, and signs the user in through the browser.
- Spring Security's authorization server runs inside the application, in its own filter chain, ahead of chapter 5's.
- A token names the MCP endpoint as its audience, and the decoder refuses a token for another audience or another issuer.
- `mcp:tools` opens the endpoint, and `@PreAuthorize` still decides each write, with the `petclinic:write` scope that the authorization server gives to staff.

Chapter 14 gives the agent three tools that write their own GraphQL, for questions that the operation files do not answer.
