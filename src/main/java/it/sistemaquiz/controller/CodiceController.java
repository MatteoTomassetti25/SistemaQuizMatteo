// File: SistemaQuiz/src/main/java/it/sistemaquiz/controller/CodiceController.java
package it.sistemaquiz.controller;

import java.util.Optional;

import org.slf4j.Logger; // Importazione per SLF4J Logger
import org.slf4j.LoggerFactory; // Importazione per SLF4J Logger
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.model.EseguiTestRequest;
import it.sistemaquiz.repository.CodiceRepository;
import it.sistemaquiz.repository.DomandaRepository;
import it.sistemaquiz.repository.UtenteRepository;
import it.sistemaquiz.service.CodiceService;
import it.sistemaquiz.service.CodiceService.ExecutionResult;
import jakarta.validation.Valid;
import it.sistemaquiz.exception.ResourceNotFoundException;


@RestController
public class CodiceController {

    // Logger SLF4J
    private static final Logger logger = LoggerFactory.getLogger(CodiceController.class);

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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        logger.warn("ResourceNotFoundException gestita nel controller: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("<div id='risultato' class='errore'>" + StringEscapeUtils.escapeHtml4(ex.getMessage()) + "</div>");
    }

    @Operation(summary = "Compila ed esegue il codice Java fornito.",
               description = "Riceve un ID domanda e codice sorgente Java, li compila ed esegue i test associati.",
               requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                   description = "Dati per l'esecuzione del test.",
                   required = true,
                   content = @Content(
                       mediaType = "application/json",
                       schema = @Schema(implementation = EseguiTestRequest.class)
                   )
               ),
               responses = {
                   @ApiResponse(responseCode = "200", description = "Test eseguiti (output HTML nel corpo).", content = @Content(mediaType = "text/html")),
                   @ApiResponse(responseCode = "400", description = "Dati di input non validi (output HTML nel corpo).", content = @Content(mediaType = "text/html", schema = @Schema(example = "<div class='errore'>...</div>"))),
                   @ApiResponse(responseCode = "401", description = "Non autorizzato (output HTML nel corpo).", content = @Content(mediaType = "text/html", schema = @Schema(example = "<div class='errore'>Non autorizzato.</div>"))),
                   @ApiResponse(responseCode = "404", description = "Risorsa non trovata (output HTML nel corpo).", content = @Content(mediaType = "text/html", schema = @Schema(example = "<div class='errore'>Risorsa non trovata.</div>"))),
                   @ApiResponse(responseCode = "500", description = "Errore interno del server (output HTML nel corpo).", content = @Content(mediaType = "text/html", schema = @Schema(example = "<div class='errore'>Errore interno.</div>")))
               })
    @PostMapping("/eseguiTest")
    public ResponseEntity<String> eseguiTest(
            @Valid @RequestBody EseguiTestRequest request
    ) {
        logger.info("Richiesta /eseguiTest ricevuta per idDomanda: {}", request.getIdDomanda());
        Long idDomanda = request.getIdDomanda();
        String codice = request.getCodice();

        String matricola;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            matricola = authentication.getName();
            logger.info("Utente autenticato con matricola: {}", matricola);
        } else {
            logger.warn("Tentativo di accesso a /eseguiTest da utente non autenticato o anonimo.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body("<div id='risultato' class='errore'>Utente non autenticato. Effettua il login.</div>");
        }

        Utente utente = utenteRepository.findByMatricola(matricola)
                .orElseThrow(() -> {
                    logger.error("Utente non trovato per matricola: {}", matricola);
                    return new ResourceNotFoundException("Utente con matricola '" + StringEscapeUtils.escapeHtml4(matricola) + "' non trovato. Impossibile procedere.");
                });
        logger.debug("Utente recuperato: ID {}, Matricola {}", utente.getId(), utente.getMatricola());

        Domanda domanda = domandaRepository.findById(idDomanda)
                .orElseThrow(() -> {
                    logger.error("Domanda non trovata per ID: {}", idDomanda);
                    return new ResourceNotFoundException("Domanda con ID " + idDomanda + " non trovata. Impossibile procedere.");
                });
        logger.debug("Domanda recuperata: ID {}", domanda.getId());
        
        ExecutionResult result;
        try {
            logger.debug("Inizio esecuzione compilazione e test per domanda ID: {} e utente matricola: {}", idDomanda, matricola);
            result = codiceService.eseguiCompilazioneETest(domanda, codice);
            logger.info("Risultato esecuzione test: Successo={}, CompilationError={}", result.overallSuccess, result.compilationError);
        } catch (Exception ex) {
            logger.error("Errore critico durante esecuzioneCompilazioneETest: ", ex);
            result = ExecutionResult.internalError("Errore critico durante l'elaborazione della compilazione/test: " + ex.getMessage());
        }

        try {
            logger.info("Tentativo di salvataggio dell'entità Codice per utente ID: {} e domanda ID: {}. Risultato test: {}", utente.getId(), domanda.getId(), result.overallSuccess);
            it.sistemaquiz.entity.Codice codiceEntity = new it.sistemaquiz.entity.Codice(codice, utente, domanda, result.overallSuccess);
            codiceRepository.save(codiceEntity);
            logger.info("Entità Codice salvata con successo con ID: {}", codiceEntity.getId());
        } catch (Exception dbEx) {
            // L' InvalidDataAccessApiUsageException dovrebbe essere lanciata da qui se c'è un problema con il save.
            // Se la catturiamo qui, non verrà gestita dal GlobalApiExceptionHandler come InvalidDataAccessApiUsageException specifica.
            // Per farla gestire globalmente e vedere il messaggio specifico, dovremmo rilanciarla o non catturare Exception così genericamente.
            // Tuttavia, per ora, la logghiamo e permettiamo che il risultato del test venga comunque inviato.
            // SE L'OBIETTIVO E' VEDERE InvalidDataAccessApiUsageException NEL GlobalApiExceptionHandler, QUESTO BLOCCO CATCH ANDREBBE RIMOSSO O MODIFICATO
            logger.error("!!! ECCEZIONE DURANTE IL SALVATAGGIO DEL CODICE NEL DATABASE !!!: ", dbEx);
            // Potresti voler aggiungere un messaggio all'HTML di `result` per indicare che il salvataggio è fallito,
            // ma il risultato del test è ancora valido.
            // Esempio: result = new ExecutionResult(result.overallSuccess, result.htmlBody + "<div class='errore-salvataggio'>Attenzione: il tuo codice è stato testato ma non è stato possibile salvare il tentativo.</div>", result.compilationError);
            // Per il momento, lascio che l'eccezione si propaghi se non la catturiamo specificamente qui,
            // o la logghiamo e continuiamo se la catturiamo.
            // Dato che il GlobalApiExceptionHandler la sta già mostrando, significa che questo catch non la sta bloccando completamente
            // o l'eccezione avviene prima di questo blocco specifico nel flusso di `codiceRepository.save()`.
            // La InvalidDataAccessApiUsageException viene lanciata da Spring Data JPA e si propaga se non catturata qui.
        }

        HttpStatus statusHttp = result.overallSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        if (result.htmlBody != null && (result.htmlBody.contains("Errore interno") || result.htmlBody.contains("Errore critico"))) {
             statusHttp = HttpStatus.INTERNAL_SERVER_ERROR;
        } else if (result.compilationError) {
            statusHttp = HttpStatus.BAD_REQUEST;
        } else if (!result.overallSuccess) {
             statusHttp = HttpStatus.BAD_REQUEST;
        }
        
        return ResponseEntity.status(statusHttp).body(result.htmlBody);
    }

    @Operation(summary = "Mostra il codice del test per una domanda specifica.")
    @ApiResponse(responseCode = "200", description = "Codice del test restituito.", content = @Content(mediaType = "text/plain"))
    @ApiResponse(responseCode = "400", description = "ID Domanda mancante.")
    @ApiResponse(responseCode = "404", description = "Domanda non trovata.")
    @GetMapping("/mostraTest")
    public ResponseEntity<String> mostraTest(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
             logger.warn("/mostraTest chiamato senza idDomanda");
             return ResponseEntity.badRequest().body("// Errore: ID Domanda mancante nella richiesta.");
        }
        logger.info("/mostraTest chiamato per idDomanda: {}", idDomanda);
        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);
        if (domandaOptional.isPresent()) {
            String test = domandaOptional.get().getTest();
            if (test == null || test.isEmpty()) {
                logger.info("Nessun test disponibile per domanda ID: {}", idDomanda);
                return ResponseEntity.ok("// Nessun test disponibile per questa domanda.");
            }
            return ResponseEntity.ok(test);
        } else {
             logger.warn("Domanda non trovata in /mostraTest per ID: {}", idDomanda);
             return ResponseEntity.status(HttpStatus.NOT_FOUND).body("// Errore: Domanda con ID " + idDomanda + " non trovata.");
        }
    }

    @Operation(summary = "Mostra la consegna di una domanda specifica.")
    @ApiResponse(responseCode = "200", description = "Consegna della domanda restituita.", content = @Content(mediaType = "text/plain"))
    @ApiResponse(responseCode = "400", description = "ID Domanda mancante.")
    @ApiResponse(responseCode = "404", description = "Domanda non trovata.")
    @GetMapping("/mostraDomanda")
    public ResponseEntity<String> mostraDomanda(@RequestParam(required = false) Long idDomanda) {
        if (idDomanda == null) {
            logger.warn("/mostraDomanda chiamato senza idDomanda");
            return ResponseEntity.badRequest().body("Errore: ID Domanda mancante nella richiesta.");
        }
        logger.info("/mostraDomanda chiamato per idDomanda: {}", idDomanda);
        Optional<Domanda> domOpt = domandaRepository.findById(idDomanda);
        if (domOpt.isPresent()) {
            String consegna = domOpt.get().getDomanda();
            if (consegna != null && !consegna.isEmpty()) {
                 return ResponseEntity.ok(consegna);
            } else {
                 logger.info("Nessuna consegna disponibile per domanda ID: {}", idDomanda);
                 return ResponseEntity.ok("Nessuna consegna disponibile per questa domanda.");
            }
        } else {
            logger.warn("Domanda non trovata in /mostraDomanda per ID: {}", idDomanda);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: Domanda con ID " + idDomanda + " non trovata.");
        }
    }
}