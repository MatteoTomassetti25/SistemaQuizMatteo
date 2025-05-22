package it.sistemaquiz.authentication;

import org.springframework.context.annotation.Bean;
// ... imports ...
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// ...

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfiguration(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AuthenticationProvider authenticationProvider
    ) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
            .csrf(csrf -> csrf.disable()) 
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) 
            .authorizeHttpRequests(auth -> auth
                .requestMatchers( 
                        "/auth/**",                     
                        "/autenticazione.html",         
                        "/swagger-ui.html",             
                        "/swagger-ui/**",               
                        "/api-docs/**",                 
                        "/schemaAutenticazione.json",   
                        "/schemaEseguiTest.json",       
                        "/htmx.min.js",                 
                        "/favicon.ico",
                        "/error"                        
                ).permitAll()
                .requestMatchers( // Endpoint protetti che richiedono autenticazione
                        "/eseguiTest",
                        "/mostraDomanda",
                        "/mostraTest",
                        "/test1.html",                  
                        "/utenti/**",  // QUESTA REGOLA COPRE GIA' /utenti/me/matricola
                        "/domande/**" 
                ).authenticated()
                .anyRequest().authenticated() 
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            
        return httpSecurity.build();
    }

    // CorsConfigurationSource rimane invariato
    // ...
}