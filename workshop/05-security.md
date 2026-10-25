# Chapter 5: Security

> **User story** · As the clinic manager, I want anyone to look the clinic up, and only staff to change its records or read a client's telephone, so that opening the API keeps the data safe.

Anyone can change anything through our API today. In this chapter, reads stay open and writes need a signed-in member of staff. Clients sign in with a GraphQL mutation that returns a signed token, and rules on the controller methods decide what each caller may do, down to a single field.

By the end of this chapter, you will:

- See how Spring Security secures an application by default, and open the reads again
- Sign in with a `login` mutation that returns a signed access token
- Protect mutations with `@PreAuthorize`
- Protect one field, and read the partial response it produces

Two users exist: `staff` with the password `staff`, who may change data, and `guest` with the password `guest`, who may only read.

## 1. Add the dependency: secure by default

Spring Security checks tokens as an OAuth 2 **resource server**: every request may carry an access token, and the server checks the token's signature before it trusts anything in it. Add the starter:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

> [!NOTE]
> With Gradle, add `implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'` to `build.gradle`.

Restart, and try GraphiQL and the web pages at http://localhost:8080. Both answer `401 Unauthorized`. With Spring Security on the classpath and without a configuration of our own, every request needs a signed-in user. That is a safe default, and it is too strict for a clinic that welcomes visitors.

## 2. The security configuration

Tokens need a key to sign them and a key to check them. The starting point has a key pair for development in `src/main/resources/keys`. Anyone can read it in the repository, so a real deployment uses a key of its own. Add the key locations below the `# GraphQL` section:

`src/main/resources/application.properties`

```properties
# Security
# The key pair that signs and checks access tokens. For development only.
spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:keys/petclinic-dev.pub
petclinic.jwt.public-key=classpath:keys/petclinic-dev.pub
petclinic.jwt.private-key=classpath:keys/petclinic-dev.key
```

**Create a new class** named `SecurityConfiguration` in the `org.springframework.samples.petclinic.system` package. It is `public`, because step 6 uses it from the tests:

`src/main/java/org/springframework/samples/petclinic/system/SecurityConfiguration.java`

```java
package org.springframework.samples.petclinic.system;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

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
import org.springframework.security.oauth2.jwt.JwtEncoder;
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

	@Bean
	JwtEncoder jwtEncoder(@Value("${petclinic.jwt.public-key}") RSAPublicKey publicKey,
			@Value("${petclinic.jwt.private-key}") RSAPrivateKey privateKey) {
		RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
	}

}
```

| Element | Meaning |
|---|---|
| `anyRequest().permitAll()` | Every request may come in. The rules about who may do what go on the controller methods, in steps 4 and 5. |
| `oauth2ResourceServer(... jwt ...)` | A request with `Authorization: Bearer <token>` is signed in as the token's user, once the signature checks out with the public key from `application.properties`. |
| `csrf.disable()` | CSRF protection guards sessions that a browser sends along by itself. Our callers sign in with a token they send on purpose, and the web pages keep working without a session. |
| `@EnableMethodSecurity` | Turns on the `@PreAuthorize` annotations of step 4. |
| `users()` | The two users, kept in memory. `{noop}` stores the password as it is, which suits a workshop only. |
| `jwtEncoder(...)` | Signs tokens with the private key. The next step uses it. |

Restart: GraphiQL and the web pages answer again.

## 3. Sign in with a mutation

Add the mutation to the schema:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Mutation {
    # ...existing mutations...
    "Signs in, and returns the access token to send as 'Authorization: Bearer <token>'"
    login(username: String!, password: String!): String!
}
```

**Create a new class** named `LoginGraphQLController` in the `org.springframework.samples.petclinic.system` package:

`src/main/java/org/springframework/samples/petclinic/system/LoginGraphQLController.java`

```java
package org.springframework.samples.petclinic.system;

import java.time.Duration;
import java.time.Instant;

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
class LoginGraphQLController {

	private final UserDetailsService users;

	private final PasswordEncoder passwordEncoder;

	private final JwtEncoder jwtEncoder;

	LoginGraphQLController(UserDetailsService users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
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
			.issuer("petclinic")
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

Restart and sign in as staff:

```graphql
mutation {
  login(username: "staff", password: "staff")
}
```

The answer is a JSON Web Token: three parts in base64, separated by dots. The middle part holds the **claims**, which anyone can read, and the signature makes them impossible to change without the private key:

```json
{ "iss": "petclinic", "sub": "staff", "exp": 1792647490, "iat": 1792618690, "scope": "petclinic:write" }
```

The `scope` says what the token allows. A `guest` token has an empty scope. Try a wrong password, and an unknown user: both answer the same `UNAUTHORIZED`, so a caller cannot find out which usernames exist.

## 4. Protect the mutations

`@PreAuthorize` checks a rule before a method runs. Spring Security turns each scope of a token into an authority named `SCOPE_` plus the scope, so the rule asks for `SCOPE_petclinic:write`. Add the annotation to both owner mutations:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Owner addOwner(@Argument AddOwnerInput input) {
	return this.ownerService.addOwner(input);
}

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Owner updateOwner(@Argument UpdateOwnerInput input) {
	return this.ownerService.updateOwner(input);
}
```

And to booking a visit:

`src/main/java/org/springframework/samples/petclinic/owner/VisitGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Visit addVisit(@Argument AddVisitInput input) {
	return this.visitService.addVisit(input);
}
```

Restart, and run the `addOwner` mutation from chapter 3 three times:

1. Without a token: `UNAUTHORIZED`.
2. As `guest`: sign in as `guest`, open the **Headers** tab below the GraphiQL editor, and add the token. The answer is `FORBIDDEN`.
3. As `staff`, with the staff token in the same place: the owner is created.

```json
{ "Authorization": "Bearer eyJhbGciOiJSUzI1NiJ9..." }
```

| Caller | Answer | Why |
|---|---|---|
| No token | `UNAUTHORIZED` | Nobody is signed in. |
| `guest` | `FORBIDDEN` | Signed in, without the scope. |
| `staff` | The new owner | Signed in, with `petclinic:write`. |

The rule sits on the method, so it applies to every caller that reaches the method. In chapter 13, AI agents call these mutations through MCP with tokens that carry the same scope, and this same annotation decides for them.

## 5. Protect a single field

The clinic wants an owner's telephone private. Field-level authorization uses the same annotation on a `@SchemaMapping`. First the schema: the field must become nullable, because a caller without the scope gets `null` there. Change the `telephone` line:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Owner {
    # ...existing fields...
    "Ten digits, such as 9521234567. Only staff can read it."
    telephone: String
}
```

Then a resolver for the field, in `OwnerGraphQLController`:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import org.springframework.graphql.data.method.annotation.SchemaMapping;

@SchemaMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
String telephone(Owner owner) {
	return owner.getTelephone();
}
```

Restart and run `owner(id: 12) { lastName telephone }` without a token:

```json
{
  "errors": [
    {
      "message": "Unauthorized",
      "path": ["owner", "telephone"],
      "extensions": { "classification": "UNAUTHORIZED" }
    }
  ],
  "data": { "owner": { "lastName": "Navarro", "telephone": null } }
}
```

The partial response from chapter 4 again: the owner comes back, and the one field the caller may not read is `null`, with an error that says why. As `guest`, the error is `FORBIDDEN`, and as `staff`, the telephone is there. Had `telephone` stayed `String!`, the `null` would have spread to the whole owner.

Refresh the frontend. A **Sign in** link appears, and an owner's telephone reads *🔒 visible to staff* until you sign in as `staff`. Sign in as `guest` and edit an owner: the form shows `FORBIDDEN`.

## 6. Keep the tests green

Spring Security changed Petclinic's own tests too. The tests of the web pages use `@WebMvcTest`, a slice that loads the web layer alone, so it runs with Spring Security's strict default and without our configuration. Two changes fix that. Add the security test starter next to the other test dependencies:

`pom.xml`

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-oauth2-resource-server-test</artifactId>
  <scope>test</scope>
</dependency>
```

Then import our configuration into each of the five web test classes: `OwnerControllerTests`, `PetControllerTests`, `VisitControllerTests`, `VetControllerTests` and `WelcomeControllerTests`, in `src/test/java`. Add the annotation above `@WebMvcTest`, with its imports (`WelcomeControllerTests` is in the same package as `SecurityConfiguration`, so it needs only the first one):

```java
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.system.SecurityConfiguration;

@Import(SecurityConfiguration.class)
```

Run `./mvnw test`: all 108 tests pass. Chapter 8 adds tests of our own.

## Exercise: protect the pet mutations

`addPet` and `updatePet` are still open to anyone. Protect them with the same rule, then sign in to the frontend as `guest` and try to add a pet.

<details>
<summary>Show solution</summary>

`src/main/java/org/springframework/samples/petclinic/owner/PetGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Pet addPet(@Argument AddPetInput input) {
	return this.petService.addPet(input);
}

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Pet updatePet(@Argument UpdatePetInput input) {
	return this.petService.updatePet(input);
}
```

</details>

## Recap

- With Spring Security on the classpath, everything is locked until a configuration says otherwise.
- A resource server trusts a request's bearer token once its signature checks out.
- A `login` mutation can issue that token; its `scope` claim says what it allows.
- `@PreAuthorize` on a controller method protects a mutation, or a single field. Spring for GraphQL reports a refusal as `UNAUTHORIZED` or `FORBIDDEN`.
- A protected field must be nullable, so that a refusal empties only that field.

Chapter 6 searches owners, vets and pets in one query.
