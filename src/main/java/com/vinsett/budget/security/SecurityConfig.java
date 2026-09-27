
package com.vinsett.budget.security;

import com.vinsett.budget.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    UserDetailsService noPasswordUsers() {
        return username -> { throw new UsernameNotFoundException("Use bearer authentication"); };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.setContentType("application/problem+json");
                    response.getWriter().write("""
                            {"type":"about:blank","title":"Não autenticado","status":401,
                             "detail":"Informe o token no cabeçalho Authorization: Bearer.",
                             "code":"UNAUTHORIZED"}
                            """);
                }))
                .addFilterBefore(new BearerTokenFilter(properties), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static final class BearerTokenFilter extends OncePerRequestFilter {
        private final AppProperties properties;

        private BearerTokenFilter(AppProperties properties) { this.properties = properties; }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ") && header.length() <= 263
                    && MessageDigest.isEqual(header.substring(7).getBytes(StandardCharsets.UTF_8),
                        properties.apiToken().getBytes(StandardCharsets.UTF_8))) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        new AccountPrincipal(properties.accountId()), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        }
    }
}
