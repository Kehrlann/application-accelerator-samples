package com.vmware.tanzu.apps.sso.accelerator.config;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class WebSecurityConfig {

	@Bean(name = "issuerTrust")
	@Order(Ordered.HIGHEST_PRECEDENCE)
	IssuerTrust issuerTrust(OAuth2ClientProperties properties, ApplicationContext context) {
		return new IssuerTrust(properties, context);
	}

	@Bean
	@DependsOn("issuerTrust")
	SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
		http
				.authorizeHttpRequests(authorizeRequests ->
						authorizeRequests
								// Permit all public paths explicitly.
								.requestMatchers("/", "/home", "/webjars/**", "/styles/**", "/images/**").permitAll()
								// Permit liveness and readiness probes paths
								.requestMatchers("/livez", "/readyz").permitAll()
								// Require authentication for all others paths.
								.anyRequest().authenticated()
				)
				// After a successful logout, redirect to /home.
				.logout(logout -> {
						logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository));
						logout.logoutSuccessUrl("/home");
				})
				.oauth2Login(withDefaults())
				.oauth2Client(withDefaults())
				.exceptionHandling(
						// When the user is not logged in and is trying to access a private page,
						// redirect to /home
						exceptionHandling -> exceptionHandling.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/home"))
				);
		return http.build();
	}

	// see: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html#oauth2login-advanced-oidc-logout
	private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
		OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
				new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

		// Sets the location that the End-User's User Agent will be redirected to
		// after the logout has been performed at the Provider
		oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");

		return oidcLogoutSuccessHandler;
	}

}
