package br.com.gustavoakira.ticketing.core.identity.infrastructure.security;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfiguration {
    @Bean public SecurityFilterChain identitySecurity(HttpSecurity http, SecurityProblemHandler problems) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()).httpBasic(b -> b.disable()).formLogin(f -> f.disable())
                .logout(l -> l.disable()).requestCache(c -> c.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.PUT, "/users/{id}/roles/organizer").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/events", "/events/{id}", "/events/{eventId}/seats", "/events/{eventId}/seats/{id}").authenticated()
                    .requestMatchers(HttpMethod.POST, "/events", "/events/{eventId}/seats/create-seats").hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PUT, "/events/{id}", "/events/{eventId}/seats/{id}").hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.PATCH, "/events/{id}/status/available").hasRole("ORGANIZER")
                    .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .build();
    }
}
