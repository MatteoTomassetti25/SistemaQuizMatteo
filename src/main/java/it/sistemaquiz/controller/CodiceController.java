package it.sistemaquiz.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.sistemaquiz.authentication.ApplicationConfiguration;
import it.sistemaquiz.entity.Codice;
import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.repository.CodiceRepository;
import it.sistemaquiz.repository.DomandaRepository;
import it.sistemaquiz.repository.UtenteRepository;
import it.sistemaquiz.service.AceEditorService;
import it.sistemaquiz.service.CodiceService;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class CodiceController {

    private final ApplicationConfiguration applicationConfiguration;

    @Autowired
    CodiceService codiceService;

    @Autowired
    CodiceRepository codiceRepository;

    @Autowired
    DomandaRepository domandaRepository;

    @Autowired
    UtenteRepository utenteRepository;

    @Autowired
    AceEditorService aceEditorService;

    public CodiceController(CodiceService codiceService, ApplicationConfiguration applicationConfiguration) {
        this.codiceService = codiceService;
        this.applicationConfiguration = applicationConfiguration;
    }

    @PostMapping("/eseguiTest")
public ResponseEntity<?> eseguiTest(@RequestParam Long idDomanda, @RequestParam String codice) {
    if (idDomanda == null) {
        return ResponseEntity.status(400).body("Domanda non trovata");
    }

    

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String matricola = "123456"; // TODO: prendi dalla sessione se necessario

    Utente utente = utenteRepository.findByMatricola(matricola)
            .orElseThrow(() -> new RuntimeException("Utente non trovato"));

    Domanda domanda = this.domandaRepository.findById(idDomanda)
            .orElseThrow(() -> new IllegalArgumentException("Domanda non trovata"));

    String test = domanda.getTest();

    String nomeClassePrincipale = codiceService.estraiNomeClasse(codice);
    String nomeClasseTest = codiceService.estraiNomeClasse(test);

    if (nomeClassePrincipale == null) {
        return ResponseEntity.badRequest()
                .body("<div class='errore'>Errore: La classe inserita non è valida</div>");
    }

    String codiceTestAggiornato = test.replace("CodiceUtente", nomeClassePrincipale);

    Map<String, String> codiceClassi = new HashMap<>();
    codiceClassi.put(nomeClassePrincipale, codice);
    codiceClassi.put(nomeClasseTest, codiceTestAggiornato);

    try {
        Map<String, Class<?>> classiCompilate = codiceService.caricaClassiCompilate(codiceClassi);
        Class<?> classeUtente = classiCompilate.get(nomeClassePrincipale);
        Class<?> classeTest = classiCompilate.get(nomeClasseTest);

        List<Map<String, String>> risultatiTest = codiceService.eseguiTestJUnit(classeUtente, classeTest);

        if (codiceService.getOutput()) {
            codiceRepository.save(new Codice(codice, utente, domanda, true));
            return ResponseEntity.ok("<div class='successo'>TEST ANDATI A BUON FINE</div>");
        } else {
            codiceRepository.save(new Codice(codice, utente, domanda, false));

            StringBuilder outputMessaggio = new StringBuilder();
            outputMessaggio.append("<div class='errore'><h3>Errore nei test:</h3><ul>");

            for (Map<String, String> risultato : risultatiTest) {
                String testName = risultato.get("test");
                String errore = risultato.get("errore");
                int lineNumber = extractLineNumberFromError(errore);

                testName = testName.replaceAll("<[^>]*>", "").replaceAll("\\s+", "");
                String testMethodName = testName.replaceAll(".*\\btest([a-zA-Z0-9_]+).*", "test$1");
                String testId = "test_" + testMethodName;

                outputMessaggio.append("<li> ")
                    .append("<a href='#' onclick='highlightErrorInEditor(").append(lineNumber).append("); return false;' ")
                    .append("data-testid='").append(testId).append("'>")
                    .append(testId)
                    .append("</a><br><b>Errore:</b> ")
                    .append(errore)
                    .append("</li>");
            }

            outputMessaggio.append("</ul></div>");

            // Utilizzo del servizio AceEditorService per generare lo script
            outputMessaggio.append(aceEditorService.createTestErrorHandlingScript());
            
            return ResponseEntity.badRequest().body(outputMessaggio.toString());
        }

    } catch (Exception e) {
        int lineNumber = extractLineNumberFromError(e.getMessage());

        String errorMessage = "<div class='errore'>Errore: " + e.getMessage() + "</div>";

        if (lineNumber > 0) {
            errorMessage += "<script>" +
                "document.addEventListener('DOMContentLoaded', function() {" +
                "   const editor = ace.edit('editor');" +
                aceEditorService.createErrorMarkerScript("editor", lineNumber, "Errore di compilazione") +
                aceEditorService.createHighlightLineScript("editor", lineNumber, "error-line") +
                "   editor.scrollToLine(" + lineNumber + ", true, true);" +
                "});" +
                "</script>";
        }

        return ResponseEntity.badRequest().body(errorMessage);
    }
}


    private int extractLineNumberFromError(String errorMessage) {
        if (errorMessage == null) return 0;
        Pattern pattern = Pattern.compile("(?:alla riga|:)(\\d+)");
        Matcher matcher = pattern.matcher(errorMessage);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @GetMapping("/mostraTest")
    public ResponseEntity<String> mostraTest(@RequestParam Long idDomanda,
                                             @RequestParam(required = false) Integer highlightLine) {
        if (idDomanda == null) {
            return ResponseEntity.badRequest().body("Domanda non disponibile");
        }

        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);

        if (domandaOptional.isPresent()) {
            String test = domandaOptional.get().getTest();
            if (test == null || test.isEmpty()) {
                return ResponseEntity.status(400).body("Nessun test disponibile");
            }

            if (highlightLine != null) {
                // Utilizzo del servizio AceEditorService per generare lo script
                String script = aceEditorService.createTestHighlightScript(highlightLine);
                return ResponseEntity.ok(test + script);
            }
            return ResponseEntity.ok(test);
        }
        return ResponseEntity.status(404).body("Domanda non trovata");
    }

    @GetMapping("/mostraDomanda")
    public ResponseEntity<String> mostraDomanda(@RequestParam Long idDomanda) {
        if (idDomanda == null) {
            return ResponseEntity.status(400).body("Domanda non disponibile");
        }

        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);

        if (domandaOptional.isPresent()) {
            String consegna = domandaOptional.get().getDomanda();
            return consegna != null && !consegna.isEmpty()
                ? ResponseEntity.ok(consegna)
                : ResponseEntity.badRequest().body("Nessuna consegna disponibile");
        }
        return ResponseEntity.status(404).body("Domanda non trovata");
    }


    
    
}
