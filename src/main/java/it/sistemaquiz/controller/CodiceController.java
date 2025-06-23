// File: src/main/java/it/sistemaquiz/controller/CodiceController.java
package it.sistemaquiz.controller;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import it.sistemaquiz.entity.Codice;
import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.model.EseguiTestRequest;
import it.sistemaquiz.repository.CodiceRepository;
import it.sistemaquiz.repository.DomandaRepository;
import it.sistemaquiz.repository.UtenteRepository;
import it.sistemaquiz.service.CodiceService;
import it.sistemaquiz.service.CodiceService.risultatoEsecuzioneTest;
import jakarta.validation.Valid;
import it.sistemaquiz.exception.ResourceNotFoundException;


@RestController
public class CodiceController {

    private static final Logger logger = LoggerFactory.getLogger(CodiceController.class);

    private final CodiceService codiceService;
    private final CodiceRepository codiceRepository;
    private final DomandaRepository domandaRepository;
    private final UtenteRepository utenteRepository;

    @Autowired
    public CodiceController(CodiceService codiceService, CodiceRepository codiceRepository, DomandaRepository domandaRepository, UtenteRepository utenteRepository) {
        this.codiceService = codiceService;
        this.codiceRepository = codiceRepository;
        this.domandaRepository = domandaRepository;
        this.utenteRepository = utenteRepository;
    }

    

    @Operation(summary = "Compila ed esegue il codice Java fornito." )
    @PostMapping("/eseguiTest")
    public ResponseEntity<String> eseguiTest(@Valid @RequestBody EseguiTestRequest request) {
        logger.info("Richiesta /eseguiTest ricevuta per idDomanda: {}", request.getIdDomanda());

        
        Utente utente = getAuthenticatedUserOrThrow();
        Domanda domanda = domandaRepository.findById(request.getIdDomanda())
                .orElseThrow(() -> new ResourceNotFoundException("Domanda con ID " + request.getIdDomanda() + " non trovata."));

        
        Optional<Codice> ultimaSottomissioneOpt = codiceRepository.findFirstByUtenteAndDomandaOrderByIdDesc(utente, domanda);
        if (ultimaSottomissioneOpt.isPresent() && request.getCodice().equals(ultimaSottomissioneOpt.get().getCodice())) {
            logger.info("Tentativo di riesecuzione con codice identico per utente {} e domanda {}. Operazione annullata.", utente.getMatricola(), domanda.getId());
            return creaRispostaPerSottomissioneIdentica(ultimaSottomissioneOpt.get());
        }

        
        risultatoEsecuzioneTest result;
        try {
            result = codiceService.eseguiCompilazioneETest(domanda, request.getCodice());
        } catch (Exception ex) {
            logger.error("Errore critico durante esecuzioneCompilazioneETest: ", ex);
            result = risultatoEsecuzioneTest.erroreGenerico("Errore critico durante l'elaborazione: " + ex.getMessage());
        }

        
        salvaOAggiornaSottomissione(ultimaSottomissioneOpt, request.getCodice(), utente, domanda, result.successo);

       
        HttpStatus statusHttp = determinaStatusHttp(result);
        return ResponseEntity.status(statusHttp).body(result.html);
    }

    
    private Utente getAuthenticatedUserOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            
            throw new SecurityException("Utente non autenticato."); 
        }
        String matricola = authentication.getName();
        return utenteRepository.findByMatricola(matricola)
                .orElseThrow(() -> new ResourceNotFoundException("Utente con matricola '" + StringEscapeUtils.escapeHtml4(matricola) + "' non trovato."));
    }
    
    
    private void salvaOAggiornaSottomissione(Optional<Codice> sottomissioneEsistenteOpt, String codiceSorgente, Utente utente, Domanda domanda, boolean esitoPositivo) {
        try {
            
            Codice codiceDaSalvare = sottomissioneEsistenteOpt.orElseGet(Codice::new);
            
           
            codiceDaSalvare.setUtente(utente);
            codiceDaSalvare.setDomanda(domanda);
            codiceDaSalvare.setCodice(codiceSorgente);
            codiceDaSalvare.setRisultato(esitoPositivo);
            
            
            Codice codiceSalvato = codiceRepository.save(codiceDaSalvare);
            logger.info("Entità Codice salvata/aggiornata con successo. ID: {}", codiceSalvato.getId());
        } catch (Exception dbEx) {
            
            logger.error("!!! ECCEZIONE DURANTE IL SALVATAGGIO/AGGIORNAMENTO DEL CODICE NEL DATABASE !!!: ", dbEx);
        }
    }
    
    
    private HttpStatus determinaStatusHttp(risultatoEsecuzioneTest result) {
        if (result.html != null && (result.html.contains("Errore interno") || result.html.contains("Errore critico"))) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (result.erroriCompilazione || !result.successo) {
            return HttpStatus.BAD_REQUEST; // 400 per fallimenti di compilazione o test
        }
        return HttpStatus.OK; // 200 solo per successo pieno
    }

    
    private ResponseEntity<String> creaRispostaPerSottomissioneIdentica(Codice ultimaSottomissione) {
        String messaggio = "Il codice sottomesso è identico all'ultimo tentativo. Non verrà eseguito di nuovo.";
        String esitoPrecedente = ultimaSottomissione.isRisultato()
            ? "<span class='successo-precedente'>L'ultimo tentativo con questo codice ha avuto successo.</span>"
            : "<span class='errore-precedente'>L'ultimo tentativo con questo codice è fallito.</span>";

        String htmlResponse = "<div id='risultato' class='info'>"
                            + "<h3>Operazione Annullata</h3>"
                            + "<p>" + StringEscapeUtils.escapeHtml4(messaggio) + "</p>"
                            + esitoPrecedente
                            + "</div>";
        
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(htmlResponse);
    }

   
    @GetMapping("/mostraTest")
    public ResponseEntity<String> mostraTest(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
            return ResponseEntity.badRequest().body("// Errore: ID Domanda mancante nella richiesta.");
        }
        return domandaRepository.findById(idDomanda)
                .map(domanda -> ResponseEntity.ok(Optional.ofNullable(domanda.getTest()).orElse("// Nessun test disponibile per questa domanda.")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("// Errore: Domanda con ID " + idDomanda + " non trovata."));
    }

    @GetMapping("/mostraDomanda")
    public ResponseEntity<String> mostraDomanda(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
            return ResponseEntity.badRequest().body("Errore: ID Domanda mancante nella richiesta.");
        }
        return domandaRepository.findById(idDomanda)
                .map(domanda -> ResponseEntity.ok(Optional.ofNullable(domanda.getDomanda()).orElse("Nessuna consegna disponibile per questa domanda.")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: Domanda con ID " + idDomanda + " non trovata."));
    }
}