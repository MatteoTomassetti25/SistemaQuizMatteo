package it.sistemaquiz.controller; // Assicurati che il package sia corretto

import java.util.Optional;

import org.apache.commons.lang3.StringEscapeUtils;
// Rimosso import logger
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // Opzionale: per Spring Security
import org.springframework.security.core.context.SecurityContextHolder; // Opzionale: per Spring Security
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
// Import Entità, Repository, Service e il risultato custom
import it.sistemaquiz.entity.Codice;
import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.repository.CodiceRepository;
import it.sistemaquiz.repository.DomandaRepository;
import it.sistemaquiz.repository.UtenteRepository;
import it.sistemaquiz.service.CodiceService;
import it.sistemaquiz.service.CodiceService.ExecutionResult; // Importa la classe interna


@RestController
public class CodiceController {

    // Rimosso campo logger

    private final CodiceService codiceService;
    private final CodiceRepository codiceRepository;
    private final DomandaRepository domandaRepository;
    private final UtenteRepository utenteRepository;

    @Autowired
    public CodiceController(CodiceService codiceService,
                            CodiceRepository codiceRepository,
                            DomandaRepository domandaRepository,
                            UtenteRepository utenteRepository) {
        this.codiceService = codiceService;
        this.codiceRepository = codiceRepository;
        this.domandaRepository = domandaRepository;
        this.utenteRepository = utenteRepository;
    }

    @ApiResponse
    @PostMapping("/eseguiTest")
    public ResponseEntity<String> eseguiTest(@RequestParam(required = false) Long idDomanda,
                                             @RequestParam(required = false) String codice) {

        if (idDomanda == null) {
            return ResponseEntity.badRequest().body("<div class='errore'>ID Domanda mancante.</div>");
        }
        if (codice == null || codice.trim().isEmpty()) {
             return ResponseEntity.badRequest().body("<div class='errore'>Il codice fornito non può essere vuoto.</div>");
        }

        String matricola;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            matricola = authentication.getName();
        } else {
            matricola = "123456"; 
        }

        Utente utente;
        Optional<Domanda> domandaOpt;
        try {
             utente = utenteRepository.findByMatricola(matricola)
                    .orElseThrow(() -> new ResourceNotFoundException("Utente '" + StringEscapeUtils.escapeHtml4(matricola) + "' non trovato."));

             domandaOpt = domandaRepository.findById(idDomanda);
             if (domandaOpt.isEmpty()) {
                  throw new ResourceNotFoundException("Domanda con ID " + idDomanda + " non trovata.");
             }

        } catch (ResourceNotFoundException rnfe) {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body("<div class='errore'>" + rnfe.getMessage() + "</div>");
        } catch (Exception e) {
             // Log rimosso, ma potresti voler gestire l'eccezione in modo diverso
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("<div class='errore'>Errore interno durante il recupero dei dati.</div>");
        }

        Domanda domanda = domandaOpt.get();

        ExecutionResult result;
        try {
            result = codiceService.eseguiCompilazioneETest(domanda, codice);
        } catch (Exception ex) {
            // Log rimosso
            result = ExecutionResult.internalError("Errore critico durante l'elaborazione: " + ex.getMessage());
        }

         try {
             codiceRepository.save(new Codice(codice, utente, domanda, result.overallSuccess));
         } catch (Exception dbEx) {
             // Log rimosso, errore DB non bloccante per la risposta utente
         }

        HttpStatus status = result.overallSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        if (result.htmlBody.contains("Errore interno") || result.htmlBody.contains("Errore critico")) {
             status = HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (result.compilationError) {
            status = HttpStatus.BAD_REQUEST;
        } else if (!result.overallSuccess) {
             status = HttpStatus.BAD_REQUEST;
        }

        return ResponseEntity.status(status).body(result.htmlBody);
    }


    @ApiResponse
    @GetMapping("/mostraTest")
    public ResponseEntity<String> mostraTest(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
             return ResponseEntity.badRequest().body("// Errore: ID Domanda mancante nella richiesta.");
        }
        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);
        if (domandaOptional.isPresent()) {
            String test = domandaOptional.get().getTest();
            if (test == null || test.isEmpty()) {
                return ResponseEntity.ok("// Nessun test disponibile per questa domanda.");
            }
            return ResponseEntity.ok(test);
        } else {
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body("// Errore: Domanda con ID " + idDomanda + " non trovata.");
        }
    }

    @ApiResponse
    @GetMapping("/mostraDomanda")
    public ResponseEntity<String> mostraDomanda(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
             return ResponseEntity.badRequest().body("Errore: ID Domanda mancante nella richiesta.");
        }
        Optional<Domanda> domOpt = domandaRepository.findById(idDomanda);
        if (domOpt.isPresent()) {
            String consegna = domOpt.get().getDomanda();
            if (consegna != null && !consegna.isEmpty()) {
                 return ResponseEntity.ok(consegna);
            } else {
                 return ResponseEntity.ok("Nessuna consegna disponibile per questa domanda.");
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: Domanda con ID " + idDomanda + " non trovata.");
        }
    }

    // Classe eccezione helper
    private static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}