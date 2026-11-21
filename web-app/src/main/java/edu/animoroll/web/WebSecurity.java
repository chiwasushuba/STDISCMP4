package edu.animoroll.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class WebSecurity {
  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
    var paths = PathPatternRequestMatcher.withDefaults();
    var api = paths.matcher("/api/**");
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET, "/login", "/index.html", "/assets/**", "/api/session")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/login")
                    .permitAll()
                    .requestMatchers("/api/faculty/**")
                    .hasRole("FACULTY")
                    .requestMatchers(
                        "/api/courses", "/api/enrollment", "/api/grades", "/api/enroll")
                    .hasRole("STUDENT")
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .defaultAuthenticationEntryPointFor(
                        (request, response, exception) ->
                            jsonError(json, response, 401, "Please sign in again."),
                        api)
                    .defaultAuthenticationEntryPointFor(
                        (request, response, exception) -> response.sendRedirect("/login"),
                        paths.matcher("/**"))
                    .defaultAccessDeniedHandlerFor(
                        (request, response, exception) ->
                            jsonError(
                                json,
                                response,
                                403,
                                "This request is not permitted. Reload the page and try again."),
                        api))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        policy ->
                            policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
        .build();
  }

  private static void jsonError(
      ObjectMapper json, HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(response.getWriter(), new PortalData.Message(message));
  }
}
