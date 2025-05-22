package it.sistemaquiz.authentication;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException; // Importato per una gestione più specifica
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.model.LoginUtente;
import it.sistemaquiz.model.RegisterUtente;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

// Per il logging, se necessario:
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {

    private final JwtService jwtService;
    private final AuthenticationService authenticationService;
    // private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);


    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }

    /**
     * Gestisce la registrazione di un nuovo utente.
     * Accetta i dati del form via @ModelAttribute.
     * Restituisce un frammento HTML per aggiornare l'interfaccia utente.
     * @param registerUtente DTO con i dati di registrazione.
     * @return ResponseEntity con un frammento HTML.
     */
    @PostMapping("/signup")
    public ResponseEntity<String> register(@ModelAttribute RegisterUtente registerUtente) { //
        try {
            if (authenticationService.utenteExists(registerUtente.getMatricola())) {
                // logger.warn("Tentativo di registrazione fallito: matricola {} già in uso.", registerUtente.getMatricola());
                return ResponseEntity.badRequest().body(
                    "<div id='signupResponse' class='response-area error'>Errore durante la registrazione: Matricola già in uso.</div>"
                );
            }
            Utente registeredUser = authenticationService.signup(registerUtente);
            // logger.info("Nuovo utente registrato con matricola: {}", registeredUser.getMatricola());
            return ResponseEntity.ok(
                "<div id='signupResponse' class='response-area success'>Utente " + HtmlUtils.htmlEscape(registeredUser.getMatricola()) + " registrato con successo. Ora puoi effettuare il login.</div>"
            );
        } catch (Exception e) {
            // logger.error("Errore imprevisto durante la registrazione per la matricola {}:", registerUtente.getMatricola(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                "<div id='signupResponse' class='response-area error'>Errore interno durante la registrazione. Riprova più tardi.</div>"
            );
        }
    }

    /**
     * Gestisce l'autenticazione di un utente esistente.
     * Accetta le credenziali dal form via @ModelAttribute.
     * In caso di successo, imposta un cookie HTTP-only con il token JWT e reindirizza a /test1.html.
     * In caso di fallimento (credenziali errate), restituisce un messaggio di errore HTML specifico.
     * @param loginUtente DTO con le credenziali di login.
     * @param httpServletResponse Oggetto per aggiungere il cookie alla risposta.
     * @return ResponseEntity con un frammento HTML o un header di redirect HTMX.
     */
    @PostMapping("/login")
    public ResponseEntity<String> authenticate(@ModelAttribute LoginUtente loginUtente, HttpServletResponse httpServletResponse) { //
        try {
            Utente authenticatedUser = authenticationService.authenticate(loginUtente); // Questo può lanciare BadCredentialsException
            String jwtToken = jwtService.generateToken(authenticatedUser);
            // logger.info("Utente {} autenticato con successo.", authenticatedUser.getMatricola());

            Cookie jwtCookie = new Cookie("jwtToken", jwtToken);
            jwtCookie.setHttpOnly(true);
            jwtCookie.setPath("/"); 
            jwtCookie.setMaxAge((int) (jwtService.getExpirationTime() / 1000)); 
            // Considera jwtCookie.setSecure(true); per HTTPS in produzione

            httpServletResponse.addCookie(jwtCookie);

            return ResponseEntity.ok()
                                 .header("HX-Redirect", "/test1.html") 
                                 .body("<div id='loginResponse' class='response-area success'>Login riuscito. Reindirizzamento in corso...</div>");

        } catch (BadCredentialsException e) { // Cattura specificamente l'eccezione per credenziali errate
            // logger.warn("Tentativo di login fallito per la matricola {}: Matricola o Password errate.", loginUtente.getMatricola());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED) 
                                 .body("<div id='loginResponse' class='response-area error'>Matricola o Password errate. Riprova.</div>");
        } catch (Exception e) { // Gestione per altre eccezioni impreviste durante il login
            // logger.error("Errore imprevisto durante il login per la matricola {}:", loginUtente.getMatricola(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("<div id='loginResponse' class='response-area error'>Errore interno durante il login. Riprova più tardi.</div>");
        }
    }
}