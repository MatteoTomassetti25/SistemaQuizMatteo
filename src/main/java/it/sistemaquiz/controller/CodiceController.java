package it.sistemaquiz.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import it.sistemaquiz.service.CodiceService;

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
        String matricola = "123456";

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

                    // Tolgo dal nome del test tutto cio che viene aggiunto da html
                    testName = testName.replaceAll("<[^>]*>", "").replaceAll("\\s+", "");

                    // inizializzo la variabile con dentro il solo nome del test
                    String testMethodName = testName.replaceAll(".*\\btest([a-zA-Z0-9]+).*", "test$1");

                    // creo un'ancora con dentro il nome del test
                    String testId = "test_" + testMethodName;

                    outputMessaggio
                            .append("<li> ")
                            .append("<a href='#" + testId + "' hx-get='/mostraTest?idDomanda=" + idDomanda
                                    + "&highlight=" + testId
                                    + "' hx-target='#test-unita' hx-swap='innerHTML'>")
                            .append(testId) 
                            .append("</a><br><b>Errore:</b> ")
                            .append(risultato.get("errore"))
                            .append("</li>");
                }

                outputMessaggio.append("</ul></div>");
                return ResponseEntity.badRequest().body(outputMessaggio.toString());

            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("<div class='errore'>Errore: " + e.getMessage() + "</div>");
        }
    }

    @GetMapping("/mostraTest")
    public ResponseEntity<String> mostraTest(@RequestParam(required = false) Long idDomanda,
            @RequestParam(required = false) String highlight) {
        if (idDomanda == null) {
            return ResponseEntity.badRequest().body("Domanda non disponibile");
        }

        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);

        if (domandaOptional.isPresent()) {
            String test = domandaOptional.get().getTest();

            if (test == null || test.isEmpty()) {
                return ResponseEntity.status(400).body("Nessun test disponibile");
            } else {
                
                String[] testLines = test.split("\n");
                StringBuilder testWithIds = new StringBuilder();

                for (String line : testLines) {
                    // verifico che nella linea ci sia public void test che e' la dichiarazione obbligatoria
                    if (line.toLowerCase().contains("public void test")) {
                        
                        String testMethodName = line.substring(line.indexOf("test"), line.indexOf("(")).trim();
                        
                        String testId = "test_" + testMethodName.replaceAll("[^a-zA-Z0-9]", "_");

                        if (testId.equals(highlight)) {
                            testWithIds.append("<div id='").append(testId).append("' class='test-evidenziato'>")
                                    .append(line).append("</div>");
                        } else {
                            testWithIds.append("<div id='").append(testId).append("'>").append(line).append("</div>");
                        }
                    } else {
                        testWithIds.append("<div>").append(line).append("</div>");
                    }
                }

                return ResponseEntity.ok(testWithIds.toString());

            }
        } else {
            return ResponseEntity.status(404).body("Domanda non trovata");
        }
    }

    @GetMapping("/mostraDomanda")
    public ResponseEntity<String> mostraDomanda(@RequestParam(required = false) Long idDomanda) {

        if (idDomanda == null) {

            return ResponseEntity.status(400).body(" Domanda non disponibile");
        }

        Optional<Domanda> domandaOptional = domandaRepository.findById(idDomanda);

        if (domandaOptional.isPresent()) {
            String consegna = domandaOptional.get().getDomanda();

            if (consegna == null || consegna.isEmpty()) {
                return ResponseEntity.badRequest().body("Nessuna consegna disponibile");
            } else {
                return ResponseEntity.ok(consegna);
            }
        } else {
            return ResponseEntity.status(404).body("Domanda non trovata");
        }
    }


    @GetMapping("/handle-error")
    public ResponseEntity<String> handleError(@RequestParam int lineNumber) {
        // Implementazione come sopra
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/clear-error")
    public ResponseEntity<String> clearError() {
        // Implementazione come sopra
        return ResponseEntity.ok().build();
    }
    
    
    
}