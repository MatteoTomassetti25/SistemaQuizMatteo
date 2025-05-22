// File: SistemaQuiz/src/main/java/it/sistemaquiz/exception/GlobalApiExceptionHandler.java
package it.sistemaquiz.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.InvalidDataAccessApiUsageException; // Importazione aggiunta per gestirla specificamente
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@ControllerAdvice
public class GlobalApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        LOG.warn("Errore di validazione DTO (@RequestBody) intercettato globalmente: {}", ex.getMessage());

        StringBuilder errorsHtml = new StringBuilder("<div id='risultato' class='errore'>"); 
        errorsHtml.append("<h3>ERRORE DI VALIDAZIONE DELLA RICHIESTA:</h3><ul>");
        
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        
        fieldErrors.sort((e1, e2) -> {
            String field1 = e1.getField();
            String field2 = e2.getField();
            if ("idDomanda".equals(field1)) return -1;
            if ("idDomanda".equals(field2)) return 1;
            if ("codice".equals(field1)) return -1;
            if ("codice".equals(field2)) return 1;
            return field1.compareTo(field2);
        });
        
        for (FieldError fieldError : fieldErrors) {
            LOG.debug("Dettaglio errore validazione @RequestBody - Campo: '{}', Messaggio: '{}', Valore Rifiutato: '{}'",
                fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getRejectedValue());

            String fieldName = fieldError.getField();
            String displayFieldName;
            
            switch (fieldName) {
                case "idDomanda":
                    displayFieldName = "ID Domanda";
                    break;
                case "codice":
                    displayFieldName = "Codice";
                    break;
                default:
                    displayFieldName = fieldName;
            }

            errorsHtml.append("<li>")
                      .append("<b>").append(HtmlUtils.htmlEscape(displayFieldName)).append(":</b> ")
                      .append(HtmlUtils.htmlEscape(fieldError.getDefaultMessage()));
            
            Object rejectedValue = fieldError.getRejectedValue();
            if (rejectedValue != null && 
                !(rejectedValue instanceof String && ((String)rejectedValue).trim().isEmpty()) &&
                String.valueOf(rejectedValue).length() < 100) { 
                 errorsHtml.append(" (Valore fornito: '").append(HtmlUtils.htmlEscape(String.valueOf(rejectedValue))).append("')");
            }
            errorsHtml.append("</li>");
        }
        errorsHtml.append("</ul></div>");
        
        return new ResponseEntity<>(errorsHtml.toString(), HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(
            BindException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        LOG.warn("Errore di binding/validazione DTO (@ModelAttribute) intercettato globalmente: {}", ex.getMessage());

        String targetDivId = "risultato"; 
        if (request.getDescription(false).contains("/auth/signup")) {
            targetDivId = "signupResponse";
        } else if (request.getDescription(false).contains("/auth/login")) {
            targetDivId = "loginResponse";
        }

        StringBuilder errorsHtml = new StringBuilder("<div id='").append(targetDivId).append("' class='response-area error'>"); 
        errorsHtml.append("<h3>ERRORE NEI DATI DEL FORM:</h3><ul>");
        
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        
        for (FieldError fieldError : fieldErrors) {
            LOG.debug("Dettaglio errore validazione @ModelAttribute - Campo: '{}', Messaggio: '{}', Valore Rifiutato: '{}'",
                fieldError.getField(), fieldError.getDefaultMessage(), fieldError.getRejectedValue());

            String fieldName = fieldError.getField();
            String displayFieldName = fieldName; 
            
            errorsHtml.append("<li>")
                      .append("<b>").append(HtmlUtils.htmlEscape(displayFieldName)).append(":</b> ")
                      .append(HtmlUtils.htmlEscape(fieldError.getDefaultMessage()));
            
            Object rejectedValue = fieldError.getRejectedValue();
            if (rejectedValue != null && 
                !(rejectedValue instanceof String && ((String)rejectedValue).trim().isEmpty()) &&
                String.valueOf(rejectedValue).length() < 100) { 
                 errorsHtml.append(" (Valore fornito: '").append(HtmlUtils.htmlEscape(String.valueOf(rejectedValue))).append("')");
            }
            errorsHtml.append("</li>");
        }
        errorsHtml.append("</ul></div>");
        
        return new ResponseEntity<>(errorsHtml.toString(), HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        LOG.warn("Errore di deserializzazione JSON (@RequestBody) intercettato globalmente: {}", ex.getMessage(), ex);

        String genericMessage = "ERRORE: La richiesta inviata non è in un formato JSON valido o i dati non possono essere letti correttamente.";
        
        String errorHtml = "<div id='risultato' class='errore'><h3>ERRORE NEL FORMATO DELLA RICHIESTA:</h3><p>" +
                           HtmlUtils.htmlEscape(genericMessage) +
                           "</p><p><small>Dettaglio tecnico (per debug): " + HtmlUtils.htmlEscape(ex.getClass().getSimpleName() + ". Controlla i log del server per maggiori informazioni.") + "</small></p></div>";
        
        return new ResponseEntity<>(errorHtml, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        LOG.warn("Risorsa non trovata ({}) intercettata da GlobalApiExceptionHandler: {}", ex.getClass().getName(), ex.getMessage());
        
        String errorHtml = "<div id='risultato' class='errore'><h3>Risorsa Non Trovata:</h3><p>" +
                           HtmlUtils.htmlEscape(ex.getMessage()) +
                           "</p></div>";
        return new ResponseEntity<>(errorHtml, HttpStatus.NOT_FOUND);
    }

    // Gestore specifico per InvalidDataAccessApiUsageException
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<String> handleInvalidDataAccessApiUsageException(InvalidDataAccessApiUsageException ex, WebRequest request) {
        LOG.error("ECCEZIONE DI ACCESSO AI DATI ({}) intercettata da GlobalApiExceptionHandler: {}", ex.getClass().getName(), ex.getMessage(), ex);

        String errorHtml = "<div id='risultato' class='errore'><h3>Errore nell'Accesso ai Dati:</h3><p>" +
                           "Si è verificato un problema tecnico durante l'interazione con il database. " +
                           "Controlla i log del server per dettagli specifici." +
                           "</p><p><small>Dettaglio tecnico (per debug): " + HtmlUtils.htmlEscape(ex.getClass().getSimpleName()) + ". Causa: " + HtmlUtils.htmlEscape(ex.getMostSpecificCause().getMessage()) + "</small></p></div>";
        return new ResponseEntity<>(errorHtml, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex, WebRequest request) {
        LOG.error("ECCEZIONE GENERICA NON GESTITA SPECIFICATAMENTE ({}) intercettata da GlobalApiExceptionHandler: {}", ex.getClass().getName(), ex.getMessage(), ex);

        String errorHtml = "<div id='risultato' class='errore'><h3>Errore Inatteso del Server:</h3><p>" +
                           "Si è verificato un problema durante l'elaborazione della tua richiesta. " +
                           "Per favore, riprova più tardi o contatta l'amministratore se il problema persiste." +
                           "</p><p><small>Dettaglio tecnico (per debug): " + HtmlUtils.htmlEscape(ex.getClass().getSimpleName()) + ". Controlla i log del server.</small></p></div>";
        return new ResponseEntity<>(errorHtml, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}