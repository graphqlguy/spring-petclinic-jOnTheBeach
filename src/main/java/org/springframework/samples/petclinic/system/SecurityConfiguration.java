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
