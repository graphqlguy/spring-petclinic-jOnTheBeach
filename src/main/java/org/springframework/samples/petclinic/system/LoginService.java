/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.system;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Signs in the users of {@link SecurityConfiguration} and issues their access tokens. The
 * issuer and the audience come from the properties that chapter 13 sets for the MCP
 * endpoint. Until then, the issuer is {@code petclinic}, and a token leaves the audience out.
 */
@Service
public class LoginService {

	private final UserDetailsService users;

	private final PasswordEncoder passwordEncoder;

	private final JwtEncoder jwtEncoder;

	private final String issuer;

	private final String audience;

	LoginService(UserDetailsService users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:petclinic}") String issuer,
			@Value("${gatool.mcp.security.resource:}") String audience) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.audience = audience;
	}

	/**
	 * A signed access token for the user, valid for eight hours. Staff get the
	 * {@code petclinic:write} scope, and other users an empty scope.
	 * @throws UsernameNotFoundException when no user has the name
	 * @throws BadCredentialsException when the password is wrong
	 */
	public String login(String username, String password) {
		UserDetails user = users.loadUserByUsername(username);
		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new BadCredentialsException("Wrong username or password");
		}
		boolean staff = user.getAuthorities().stream().anyMatch((a) -> a.getAuthority().equals("ROLE_STAFF"));
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer);
		if (!audience.isEmpty()) {
			claims.audience(List.of(audience));
		}
		claims.subject(user.getUsername())
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofHours(8)))
			.claim("scope", staff ? "petclinic:write" : "");
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

}
