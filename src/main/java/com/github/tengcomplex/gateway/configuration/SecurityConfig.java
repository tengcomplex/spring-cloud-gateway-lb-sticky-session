package com.github.tengcomplex.gateway.configuration;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
public class SecurityConfig {
  @Bean
  @Order(1)
  SecurityWebFilterChain allowedWebFilterChain(ServerHttpSecurity http) throws Exception {
    return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**", "/ui/**"))
        .csrf().disable()
        .authorizeExchange()
        .anyExchange().permitAll()
        .and()
        .build();
  }
}
