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

/**
 * Lets every request in, and signs in the caller of a request that carries a bearer token,
 * once the token's signature checks out with the development key pair. The rules about who
 * may do what go on the GraphQL controllers, with {@code @PreAuthorize}. Public, because the
 * tests of the web pages import it.
 */
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
