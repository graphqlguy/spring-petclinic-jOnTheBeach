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
