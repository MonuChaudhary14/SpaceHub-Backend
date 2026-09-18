package org.spacehub.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class SecurityConfiguration {

  private final Filters filter;
  private final AuthenticationProvider authenticationProvider;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  public SecurityConfiguration(Filters filter,
                               AuthenticationProvider authenticationProvider,
                               AuthenticationEntryPoint authenticationEntryPoint) {
    this.filter = filter;
    this.authenticationProvider = authenticationProvider;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public org.springframework.web.filter.CorsFilter corsFilter() {
    return new org.springframework.web.filter.CorsFilter(corsConfigurationSource());
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .cors(cors -> cors.configurationSource(corsConfigurationSource()))
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers(
          "/error",
          "/swagger-ui.html",
          "/swagger-ui/**",
          "/v3/api-docs",
          "/v3/api-docs/**",
          "/v3/api-docs.yaml",
          "/actuator/**",
          "/notification",
          "/notification/**",
          "/notifications",
          "/notifications/**",
          "/chat",
          "/chat/**",
          "/ws",
          "/ws/**"
        ).permitAll()
        .requestMatchers(
          "/api/v1/login",
          "/api/v1/registration",
          "/api/v1/validateregisterotp",
          "/api/v1/forgotpassword",
          "/api/v1/validateforgototp",
          "/api/v1/resetpassword",
          "/api/v1/resendotp",
          "/api/v1/resendforgototp",
          "/api/v1/logout",
          "/api/v1/auth/refresh"
        ).permitAll()
        .anyRequest().authenticated())
      .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
      .httpBasic(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      // .sessionManagement(session ->
      // session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
      .authenticationProvider(authenticationProvider)
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
    throws Exception {
    return authConfig.getAuthenticationManager();
  }
}
