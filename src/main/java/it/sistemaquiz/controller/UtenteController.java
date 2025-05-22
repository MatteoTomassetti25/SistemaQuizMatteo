package it.sistemaquiz.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
// ... altri import ...
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils; // Importato

import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.model.JSONResponse; 
import it.sistemaquiz.repository.UtenteRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/utenti")
@RestController
public class UtenteController {

    @Autowired
    UtenteRepository utenteRepository;

    // Metodo getCurrentUtente esistente (rimane invariato)
    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUtente(Authentication authentication) { // Modificato tipo di ritorno a ResponseEntity<String>
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            // Restituisce un frammento HTML per #protectedResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("<div id='protectedResponse' class='response-area error'>Utente non autenticato. Effettua il login per visualizzare i dettagli.</div>");
        }
        String matricola = authentication.getName(); 
        Utente utente = utenteRepository.findByMatricola(matricola)
                .orElse(null); 

        if (utente == null) {
             // Questo caso non dovrebbe accadere se l'autenticazione è andata a buon fine e il token è valido
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                  .body("<div id='protectedResponse' class='response-area error'>Errore interno: utente autenticato non trovato nel database.</div>");
        }

        // Costruisci una rappresentazione HTML dei dati utente
        // Assicurati di fare l'escape di qualsiasi dato utente per prevenire XSS
        String htmlResponse = String.format(
            "<div class='success'>" +
            "<h3>Dettagli Utente Autenticato:</h3>" +
            "<ul>" +
            "<li>ID: %s</li>" +
            "<li>Nome: %s</li>" +
            "<li>Cognome: %s</li>" +
            "<li>Matricola: %s</li>" +
            "</ul>" +
            "</div>",
            utente.getId().toString(),
            utente.getNome() != null ? HtmlUtils.htmlEscape(utente.getNome()) : "N/D",
            utente.getCognome() != null ? HtmlUtils.htmlEscape(utente.getCognome()) : "N/D",
            HtmlUtils.htmlEscape(utente.getMatricola())
        );
        // Il target #protectedResponse sarà aggiornato con questo HTML
        return ResponseEntity.ok(htmlResponse);
    }

    /**
     * Endpoint per recuperare la matricola dell'utente autenticato.
     * Restituisce un frammento HTML contenente la matricola, pronto per essere
     * renderizzato da HTMX.
     * L'accesso a questo endpoint è protetto da Spring Security (vedi SecurityConfiguration).
     *
     * @param authentication L'oggetto Authentication fornito da Spring Security,
     * contenente i dettagli dell'utente autenticato.
     * @return ResponseEntity contenente il frammento HTML con la matricola o un messaggio di errore.
     */
    @GetMapping("/me/matricola")
    public ResponseEntity<String> getMatricolaUtenteAutenticato(Authentication authentication) {
        // Controlla se l'utente è autenticato. Spring Security dovrebbe già bloccare
        // gli accessi non autorizzati, ma questo è un ulteriore controllo.
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            // In teoria, non si dovrebbe mai arrivare qui se la sicurezza è configurata correttamente.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("<div id='matricola-display' class='error-matricola' style='color: red;'>Non autorizzato</div>");
        }
        
        // authentication.getName() restituisce la matricola dell'utente, come configurato
        // in UserDetails (Utente.java -> getUsername()) e JwtAuthenticationFilter.
        String matricola = authentication.getName(); 

        // Prepara un semplice frammento HTML per la visualizzazione della matricola.
        // HtmlUtils.htmlEscape è usato per prevenire attacchi XSS.
        String htmlResponse = String.format(
            "<div id='matricola-display' style='font-weight: bold; color: #333;'>Matricola: %s</div>",
            HtmlUtils.htmlEscape(matricola)
        );
        
        // Restituisce il frammento HTML con stato HTTP 200 OK.
        return ResponseEntity.ok(htmlResponse);
    }
}