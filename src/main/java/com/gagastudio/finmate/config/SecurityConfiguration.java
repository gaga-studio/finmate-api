package com.gagastudio.finmate.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.gagastudio.finmate.api.ApiProblems;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint problemAuthenticationEntryPoint) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemAuthenticationEntryPoint))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/swagger-config", "/openapi/**").permitAll()
				.requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(problemAuthenticationEntryPoint)
				.jwt(Customizer.withDefaults()))
			.build();
	}

	@Bean
	AuthenticationEntryPoint problemAuthenticationEntryPoint(ApiProblems apiProblems) {
		return (request, response, exception) -> apiProblems.write(request, response,
			org.springframework.http.HttpStatus.UNAUTHORIZED, "invalid-credentials", "Authentication failed",
			"Authentication is required or the access token is invalid", "INVALID_CREDENTIALS");
	}

	@Bean
	JwtEncoder jwtEncoder(FinmateProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(FinmateProperties properties) {
		return NimbusJwtDecoder.withSecretKey(signingKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(FinmateProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(java.util.List.of(properties.appOrigin()));
		configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Idempotency-Key"));
		configuration.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	private SecretKey signingKey(FinmateProperties properties) {
		return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
