package it.sistemaquiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class SistemaQuizApplication implements WebMvcConfigurer {
    public static void main(String[] args) {
        SpringApplication.run(SistemaQuizApplication.class, args);
    }

     public void addViewControllers(ViewControllerRegistry registry) {
        // Aggiunge una mappatura per l'URL radice ("/").
        // Quando un utente visita "http://localhost:8080/", questa regola
        // inoltra internamente la richiesta alla risorsa statica "/autenticazione.html".
        // Spring Boot troverà il file in 'src/main/resources/static/'.
        registry.addViewController("/").setViewName("forward:/autenticazione.html");
    }
}