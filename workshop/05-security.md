# Chapter 5: Security

> **User story** · As the clinic manager, I want anyone to look the clinic up, and only staff to change its records or read a client's telephone, so that opening the API keeps the data safe.

Anyone can change anything through our API today. In this chapter, reads stay open and writes need a signed-in member of staff. Clients sign in with a GraphQL mutation that returns a signed token, and rules on the controller methods decide what each caller may do, down to a single field.

By the end of this chapter, you will:

- Know what the starting point's security setup does, and why every request may come in
- Sign in with a `login` mutation that returns a signed access token
- Protect mutations with `@PreAuthorize`
- Protect one field, and read the partial response it produces

Two users exist: `staff` with the password `staff`, who may change data, and `guest` with the password `guest`, who may only read.

## 1. What the starting point sets up

Spring Security is already in the starting point, set up as an OAuth 2 **resource server**: every request may carry an access token, and the server checks the token's signature before it trusts anything in it. The setup is plain Spring Security, so the starting point holds it, and this chapter writes the GraphQL part:

| Where | What it does |
|---|---|
| `SecurityConfiguration`, in the `system` package | Lets every request in, with `anyRequest().permitAll()`, so GraphiQL, the frontend and the web pages keep working without signing in. The rules about who may do what go on our controller methods, in steps 3 and 4. |
| The same class | Signs in a request that carries `Authorization: Bearer <token>`, once the token's signature checks out. |
| The same class | Turns CSRF protection off. It guards sessions that a browser sends along by itself, and our callers send their token on purpose. |
| The same class | Keeps the two users in memory, and turns on `@PreAuthorize` with `@EnableMethodSecurity`. |
| `src/main/resources/keys`, named in the `# Security` properties | The key pair that signs and checks the tokens. Anyone can read it in the repository, so a real deployment uses a key of its own. |
| `LoginService`, in the `system` package | Checks a password and signs a token, with the `petclinic:write` scope for staff. |

## 2. Sign in with a mutation

Once the next step protects the mutations, a client needs a token to change data. A `login` field lets the frontend get one with the same GraphQL client it uses for everything else. It is a mutation because signing in is an action on the server, which checks a password and issues a token. Add it to the schema:

`src/main/resources/graphql/schema.graphqls`

```graphql
type Mutation {
    # ...existing mutations...
    "Signs in, and returns the access token to send as 'Authorization: Bearer <token>'"
    login(username: String!, password: String!): String!
}
```

**Create a new class** named `LoginGraphQLController` in the `org.springframework.samples.petclinic.system` package, next to `LoginService`:

`src/main/java/org/springframework/samples/petclinic/system/LoginGraphQLController.java`

```java
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

## 3. Protect the mutations

`@PreAuthorize` checks a rule before a method runs. Spring Security turns each scope of a token into an authority named `SCOPE_` plus the scope, so the rule asks for `SCOPE_petclinic:write`. Add the annotation to both owner mutations:

`src/main/java/org/springframework/samples/petclinic/owner/OwnerGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Owner addOwner(@Argument AddOwnerInput input) {
	return ownerService.addOwner(input);
}

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Owner updateOwner(@Argument UpdateOwnerInput input) {
	return ownerService.updateOwner(input);
}
```

And to booking a visit:

`src/main/java/org/springframework/samples/petclinic/owner/visit/VisitGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Visit addVisit(@Argument AddVisitInput input) {
	return visitService.addVisit(input);
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

## 4. Protect a single field

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

## Exercise: protect the pet mutations

`addPet` and `updatePet` are still open to anyone. Protect them with the same rule, then sign in to the frontend as `guest` and try to add a pet.

<details>
<summary>Show solution</summary>

`src/main/java/org/springframework/samples/petclinic/owner/pet/PetGraphQLController.java`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Pet addPet(@Argument AddPetInput input) {
	return petService.addPet(input);
}

@MutationMapping
@PreAuthorize("hasAuthority('SCOPE_petclinic:write')")
Pet updatePet(@Argument UpdatePetInput input) {
	return petService.updatePet(input);
}
```

</details>

## Recap

- The starting point's resource server lets every request in, and trusts a request's bearer token once its signature checks out.
- A `login` mutation can issue that token; its `scope` claim says what it allows.
- `@PreAuthorize` on a controller method protects a mutation, or a single field. Spring for GraphQL reports a refusal as `UNAUTHORIZED` or `FORBIDDEN`.
- A protected field must be nullable, so that a refusal empties only that field.

Chapter 6 searches owners, vets and pets in one query.
