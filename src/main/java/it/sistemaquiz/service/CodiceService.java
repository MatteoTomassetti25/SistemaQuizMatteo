package it.sistemaquiz.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections; // Per Collections.singletonMap
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//javax.tools è parte del JDK standard, non richiede dipendenze Maven aggiuntive se si usa un JDK
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.apache.commons.lang3.StringEscapeUtils; // Richiede Apache Commons Lang
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.compilazioniTest; // Assicurati che esista
import it.sistemaquiz.repository.compilazioneTestRepository; // Assicurati che esista

@Service
public class CodiceService {

    private static final Logger logger = LoggerFactory.getLogger(CodiceService.class);
    private final compilazioneTestRepository compilazioneTestRepository;

    @Autowired
    public CodiceService(compilazioneTestRepository compilazioneTestRepository) {
        this.compilazioneTestRepository = compilazioneTestRepository;
    }

    // Classe interna per il risultato dell'esecuzione
    public static class risultatoEsecuzioneTest {
        public final boolean successo;
        public final String html;
        public final boolean erroriCompilazione;

        public risultatoEsecuzioneTest(boolean successo, String html, boolean erroriCompilazione) {
            this.successo = successo;
            this.html = html;
            this.erroriCompilazione = erroriCompilazione;
        }
        public static risultatoEsecuzioneTest successo() {
            return new risultatoEsecuzioneTest(true, "<div class='successo'>TEST PASSATI CON SUCCESSO!</div>", false);
        }
        public static risultatoEsecuzioneTest erroreCompilazione(String erroreHtml) {
            return new risultatoEsecuzioneTest(false, erroreHtml, true);
        }
        public static risultatoEsecuzioneTest fallimentoTest(String fallimentoTestHtml) {
            return new risultatoEsecuzioneTest(false, fallimentoTestHtml, false);
        }
        public static risultatoEsecuzioneTest erroreGenerico(String message) {
            String html = String.format("<div class='errore'>Errore generico: %s</div>", StringEscapeUtils.escapeHtml4(message));
            return new risultatoEsecuzioneTest(false, html, false);
        }
    }

    // Metodo per calcolare l'hash SHA-256 di una stringa
    public String calculateSHA256(String text) {
        if (text == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Algoritmo SHA-256 non trovato", e);
            throw new RuntimeException("Impossibile calcolare l'hash SHA-256", e);
        }
    }
    
    // Estrae il nome della classe principale dal codice sorgente
    public String estraiNomeClasse(String codice) {
        if (codice == null) return null;
        // Pattern migliorato per gestire generics, extends, implements
        Pattern pattern = Pattern.compile("\\b(?:public\\s+)?(?:abstract\\s+|final\\s+)?class\\s+([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*(?:<[^>]+>)?(?:extends\\s+\\S+)?(?:implements\\s+\\S+(?:,\\s*\\S+)*)?\\s*\\{");
        Matcher matcher = pattern.matcher(codice);
        if (matcher.find()) {
            return matcher.group(1);
        }
        logger.warn("Nome classe non trovato nel codice fornito tramite regex.");
        return null; 
    }

    @Transactional
    public risultatoEsecuzioneTest eseguiCompilazioneETest(Domanda domanda, String codiceSorgenteUtente) {
        if (codiceSorgenteUtente == null || codiceSorgenteUtente.trim().isEmpty()){
            return risultatoEsecuzioneTest.erroreGenerico("Codice sorgente utente non disponibile per la domanda.");
        }
        String nomeClasseUtente = estraiNomeClasse(codiceSorgenteUtente);
        if (nomeClasseUtente == null) {
            logger.error("Impossibile estrarre il nome della classe utente dal codice sorgente.");
            return risultatoEsecuzioneTest.erroreGenerico("Impossibile determinare il nome della classe principale dal codice utente fornito.");
        }

        logger.debug(" Inizio compilazione codice utente '{}'", nomeClasseUtente);
        CompiledCode userCompiled;
        try {
            // Compila solo il codice utente
            userCompiled = compilaSorgenti(Collections.singletonMap(nomeClasseUtente, codiceSorgenteUtente), nomeClasseUtente);
        } catch (CompilationFailedException e) {
            logger.warn("Compilazione codice utente '{}' fallita: {}", nomeClasseUtente, e.getHtmlErrorOutput());
            return risultatoEsecuzioneTest.erroreCompilazione(e.getHtmlErrorOutput());
        } catch (Exception e) {
            logger.error("Errore imprevisto durante la compilazione del codice utente '{}'", nomeClasseUtente, e);
            return risultatoEsecuzioneTest.erroreGenerico("Errore durante la compilazione del codice utente: " + e.getMessage());
        }

        Map<String, byte[]> user2Bytecodes = userCompiled.bytecodes;
        logger.info("Compilazione codice utente '{}' riuscita.", nomeClasseUtente);

        
        String codiceTestOriginale = domanda.getTest();
        if (codiceTestOriginale == null || codiceTestOriginale.trim().isEmpty()) {
            logger.error("Codice di test non disponibile per la domanda ID: {}", domanda.getId());
            return risultatoEsecuzioneTest.erroreGenerico("Codice di test non disponibile per la domanda.");
        }
        // Sostituisce il segnaposto con il nome effettivo della classe utente
        String codiceTestModificato = codiceTestOriginale.replace("CodiceUtente", nomeClasseUtente);
        String nomeClasseTest = estraiNomeClasse(codiceTestModificato);
        if (nomeClasseTest == null) {
            logger.error("Impossibile estrarre il nome della classe di test dal codice di test modificato.");
            return risultatoEsecuzioneTest.erroreGenerico("Impossibile determinare il nome della classe di test.");
        }

        String hashTest = calculateSHA256(codiceTestModificato);
        logger.debug(" Gestione test '{}' per domanda ID {}. Hash sorgente test: {}", nomeClasseTest, domanda.getId(), hashTest);

        //creo la mappa che contiene l'hash calcolato e i byte del codice per verificare la hit
        //nel caso di codice già compilato i byte saranno già esistenti nella mappa altrimenti verrà creata una nuova riga nel db e nella mappa
        Map<String, byte[]> test2Bytecodes;
        //cerco hit nelle compilazioni precedenti
        Optional<compilazioniTest> testAnalizzato = compilazioneTestRepository.findByHashTestAndDomanda(hashTest, domanda);

        //caso in cui l'hash di una classe di test già compilata hitta
        if (testAnalizzato.isPresent()) {
            compilazioniTest testEsistente = testAnalizzato.get();
            testEsistente.setUltimoUtilizzo(LocalDateTime.now());
            compilazioneTestRepository.save(testEsistente); // Aggiorna 'lastAccessedAt'
            logger.info("Cache HIT per test '{}' (hash: {}), domanda ID: {}", nomeClasseTest, hashTest, domanda.getId());

            //controlla eventuali fallimenti della compilazione dei test precedenti e indica l'eventuale errore
            if (!testEsistente.isSuccessoCompilazione()) {
                logger.warn("Compilazione test '{}' fallita (recuperata da cache): {}", nomeClasseTest, testEsistente.getOutCompilazione());
                return risultatoEsecuzioneTest.erroreCompilazione(testEsistente.getOutCompilazione());
            }
            // pesco i bytcode già compilati presenti nel db e li inserisco nella mappa usata per la compilazione del codice sorgente utente
            test2Bytecodes = Collections.singletonMap(testEsistente.getNomeClasseTest(), testEsistente.getBytecode());
            logger.info("Bytecode per test '{}' recuperati con successo dalla cache.", nomeClasseTest);

            //caso in cui non hitto perchè non ho mai compilato la classe di test
        } else {
            logger.info("Cache MISS per test '{}' (hash: {}), domanda ID: {}. Esecuzione compilazione congiunta.", nomeClasseTest, hashTest, domanda.getId());
            
            //preparo la mappa per la compilazione del codice di test
            Map<String, String> sorgenti2CompilazioneCongiunta = new HashMap<>();
            sorgenti2CompilazioneCongiunta.put(nomeClasseUtente, codiceSorgenteUtente);
            sorgenti2CompilazioneCongiunta.put(nomeClasseTest, codiceTestModificato);
            
            // creo una nuova riga del db dove vengono salvate le classi di test
            compilazioniTest nuovoTest = new compilazioniTest();
            nuovoTest.setHashTest(hashTest);
            nuovoTest.setDomanda(domanda);
            nuovoTest.setNomeClasseTest(nomeClasseTest); // Nome della classe di test estratto dal sorgente modificato

            try {
                // Compila INSIEME utente e test per risolvere dipendenze.
                CompiledCode congiuntaCompiled = compilaSorgenti(sorgenti2CompilazioneCongiunta, nomeClasseUtente, nomeClasseTest);
                
                // Estrai i bytecode specifici per la classe di test dal risultato della compilazione congiunta
                byte[] specificTestBytecode = congiuntaCompiled.bytecodes.get(nomeClasseTest);
                if (specificTestBytecode == null) {
                    logger.error("Bytecode per la classe test '{}' non trovato dopo compilazione congiunta.", nomeClasseTest);
                    throw new IllegalStateException("Bytecode della classe test non generato correttamente.");
                }

                //popolo la nuova riga del db creata in precedenza
                nuovoTest.setSuccessoCompilazione(true);
                nuovoTest.setBytecode(specificTestBytecode);
                nuovoTest.setOutCompilazione("<div class='successo-compilazione'>Test compilato con successo (cache miss).</div>"); // Messaggio generico
                compilazioneTestRepository.save(nuovoTest);
                
                // uso la nuova riga creata con i nuovi dati per l'esecuzione dei test
                test2Bytecodes = Collections.singletonMap(nomeClasseTest, specificTestBytecode);
                logger.info("Compilazione congiunta riuscita. Test '{}' salvato in cache e bytecode estratti.", nomeClasseTest);

            } catch (CompilationFailedException e) {
                // Se la compilazione congiunta fallisce, determina se l'errore è principalmente nel codice utente o nel test
                if (e.isErrorInClass(nomeClasseUtente) && !e.isErrorInClass(nomeClasseTest)) {
                    logger.warn("Errore di compilazione nel codice utente '{}' durante compilazione congiunta: {}", nomeClasseUtente, e.getHtmlErrorOutput());
                    // L'errore dell'utente dovrebbe essere stato già catturato, ma se emerge qui, lo riportiamo.
                    return risultatoEsecuzioneTest.erroreCompilazione(e.getHtmlErrorOutput());
                } else { // Errore nel test o errore non chiaramente attribuibile solo all'utente
                    logger.warn("Errore di compilazione attribuito al test '{}' o errore generale durante compilazione congiunta: {}", nomeClasseTest, e.getHtmlErrorOutput());
                    nuovoTest.setSuccessoCompilazione(false);
                    nuovoTest.setOutCompilazione(e.getHtmlErrorOutput());
                    compilazioneTestRepository.save(nuovoTest);
                    return risultatoEsecuzioneTest.erroreCompilazione(e.getHtmlErrorOutput());
                }
            } catch (Exception e) {
                logger.error("Errore imprevisto durante la compilazione congiunta per test '{}'", nomeClasseTest, e);
                return risultatoEsecuzioneTest.erroreGenerico("Errore durante la compilazione congiunta: " + e.getMessage());
            }
        }

        // --- FASE 3: Caricamento Classi ed Esecuzione Test JUnit ---
        logger.debug("FASE 3: Inizio caricamento classi ed esecuzione JUnit per utente '{}' e test '{}'.", nomeClasseUtente, nomeClasseTest);
        Map<String, byte[]> all2Bytecodes = new HashMap<>();
        all2Bytecodes.putAll(user2Bytecodes); // Bytecode utente dalla FASE 1
        all2Bytecodes.putAll(test2Bytecodes); // Bytecode test dalla FASE 2 (cache o nuova compilazione)

        try {
            Map<String, Class<?>> classiCaricate = caricaClassiDaBytecodes(all2Bytecodes);
            Class<?> classeUtenteCaricata = classiCaricate.get(nomeClasseUtente);
            Class<?> classeTestCaricata = classiCaricate.get(nomeClasseTest);

            if (classeUtenteCaricata == null) {
                logger.error("Classe utente '{}' non caricata correttamente per JUnit.", nomeClasseUtente);
                return risultatoEsecuzioneTest.erroreGenerico("Errore interno: classe utente non caricata.");
            }
            if (classeTestCaricata == null) {
                 logger.error("Classe test '{}' non caricata correttamente per JUnit.", nomeClasseTest);
                return risultatoEsecuzioneTest.erroreGenerico("Errore interno: classe di test non caricata.");
            }

            TestResult risultatoTest = eseguiTestJUnit(classeUtenteCaricata, classeTestCaricata);
            logger.info("Esecuzione JUnit completata per utente '{}', test '{}'. Successo: {}", nomeClasseUtente, nomeClasseTest, risultatoTest.success);
            return risultatoTest.success ? risultatoEsecuzioneTest.successo() : risultatoEsecuzioneTest.fallimentoTest(formattaFallimentiTestHtml(risultatoTest.failures));
        } catch (Exception e) {
            logger.error("Errore durante l'esecuzione dei test JUnit per utente '{}', test '{}'", nomeClasseUtente, nomeClasseTest, e);
            return risultatoEsecuzioneTest.erroreGenerico("Errore durante l'esecuzione dei test: " + e.getMessage());
        }
    }
    
    // Struttura per contenere i bytecodes compilati
    private static class CompiledCode {
        final Map<String, byte[]> bytecodes;
        CompiledCode(Map<String, byte[]> bytecodes) {
            this.bytecodes = bytecodes;
        }
    }

    // Eccezione personalizzata per errori di compilazione
    private static class CompilationFailedException extends Exception {
        private final String htmlErrorOutput;
        private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
        // Nomi delle classi che si tentava di compilare, per aiutare a identificare la fonte dell'errore
        private final String[] primaryClassNamesAttempted; 

        public CompilationFailedException(String htmlErrorOutput, List<Diagnostic<? extends JavaFileObject>> diagnostics, String... primaryClassNamesAttempted) {
            super("Errore di compilazione rilevato.");
            this.htmlErrorOutput = htmlErrorOutput;
            this.diagnostics = diagnostics;
            this.primaryClassNamesAttempted = primaryClassNamesAttempted;
        }
        public String getHtmlErrorOutput() { return htmlErrorOutput; }

        // Verifica se un errore di compilazione è specificamente originato da una delle classi target.
        public boolean isErrorInClass(String targetClassName) {
            if (diagnostics == null || targetClassName == null || targetClassName.trim().isEmpty()) return false;
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                if (diagnostic.getSource() != null) {
                    // Il nome del sorgente è tipicamente un URI tipo string:///com/example/MyClass.java
                    String sourceName = diagnostic.getSource().getName(); 
                    // Verifica se il nome del file sorgente nell'errore corrisponde (ignorando il path)
                    if (sourceName.endsWith("/" + targetClassName + ".java")) {
                        return true;
                    }
                }
            }
            // Se non si trova una corrispondenza esatta nel nome del file sorgente del diagnostico,
            // e si stava compilando una sola classe, si assume che l'errore sia in quella.
            if (primaryClassNamesAttempted != null && primaryClassNamesAttempted.length == 1 && primaryClassNamesAttempted[0].equals(targetClassName)) {
                return true; 
            }
            return false;
        }
    }
    
    // Compila i sorgenti forniti. I primaryClassNamesAttempted sono usati per il debugging e l'attribuzione degli errori.
    private CompiledCode compilaSorgenti(Map<String, String> codiceClassi, String... primaryClassNamesAttempted) 
            throws CompilationFailedException, RuntimeException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            logger.error("JavaCompiler di sistema non trovato.");
            throw new RuntimeException("JavaCompiler di sistema non disponibile. Assicurarsi che l'applicazione sia eseguita con un JDK, non solo un JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostica = new DiagnosticCollector<>();
        
        try (JavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostica, null, null)) {
            if (standardFileManager == null) {
                logger.error("Java Standard File Manager non trovato.");
                throw new RuntimeException("Java Standard File Manager non trovato.");
            }
            
            GestoreCompilazioneInMemoria gestoreCompilazione = new GestoreCompilazioneInMemoria(standardFileManager);
            List<JavaFileObject> filesDaCompilare = new ArrayList<>();
            for (Map.Entry<String, String> entry : codiceClassi.entrySet()) {
                String nomeClasse = entry.getKey(); 
                String codice = entry.getValue();
                if (nomeClasse == null || nomeClasse.trim().isEmpty() || !nomeClasse.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*")) {
                    logger.error("Nome classe non valido fornito per la compilazione: '{}'", nomeClasse);
                    throw new IllegalArgumentException("Nome classe non valido rilevato per la compilazione: '" + nomeClasse + "'");
                }
                filesDaCompilare.add(new CodiceSorgenteStringa(nomeClasse, codice));
            }

            if (filesDaCompilare.isEmpty()) {
                logger.warn("Nessun file sorgente fornito per la compilazione.");
                return new CompiledCode(new HashMap<>()); // Ritorna mappa vuota se non c'è nulla da compilare
            }

            // Opzioni di compilazione (esempio: debugging info)
            // List<String> options = new ArrayList<>(Arrays.asList("-g")); 

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, // PrintWriter per output aggiuntivo del compilatore (es. System.err)
                gestoreCompilazione, 
                diagnostica, 
                null, // List<String> options, 
                null, // Iterable<String> classes per annotation processing,
                filesDaCompilare
            );
            
            boolean successoCompilazione = task.call();

            if (!successoCompilazione) {
                throw new CompilationFailedException(
                    formattaErroriCompilazioneHtml(diagnostica, primaryClassNamesAttempted), 
                    diagnostica.getDiagnostics(),
                    primaryClassNamesAttempted
                );
            }
            
            return new CompiledCode(gestoreCompilazione.getAllBytecodes());

        } catch (IOException e) {
            logger.error("Errore I/O durante l'accesso al JavaFileManager o durante la compilazione.", e);
            throw new RuntimeException("Errore I/O durante la compilazione.", e);
        }
    }
    
    // Carica classi da una mappa di bytecode
    private Map<String, Class<?>> caricaClassiDaBytecodes(Map<String, byte[]> bytecodes) throws ClassNotFoundException {
        if (bytecodes == null || bytecodes.isEmpty()) {
            logger.warn("Tentativo di caricare classi da una mappa di bytecode vuota o nulla.");
            return new HashMap<>();
        }
        Map<String, Class<?>> classiCaricate = new HashMap<>();
        // Usa il classloader del contesto corrente come parent per la visibilità delle librerie (es. JUnit)
        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        if (parentClassLoader == null) { // Fallback se il classloader di contesto è nullo
            parentClassLoader = ClassLoader.getSystemClassLoader();
        }
        
        CaricatoreClassiInMemoria classLoader = new CaricatoreClassiInMemoria(bytecodes, parentClassLoader);
        
        for (String nomeClasse : bytecodes.keySet()) {
            if (bytecodes.get(nomeClasse) != null && bytecodes.get(nomeClasse).length > 0) {
                logger.debug("Caricamento classe: {}", nomeClasse);
                classiCaricate.put(nomeClasse, classLoader.loadClass(nomeClasse));
            } else {
                logger.warn("Bytecode nullo o vuoto per la classe: {}, impossibile caricarla.", nomeClasse);
            }
        }
        return classiCaricate;
    }

    // Formatta errori di compilazione in HTML, identificando l'editor target
    private String formattaErroriCompilazioneHtml(DiagnosticCollector<JavaFileObject> diagnostica, String... classNamesBeingCompiled) {
        StringBuilder messaggioErroreHtml = new StringBuilder("<div class='errore'><h3>Errore di Compilazione");
        String editorHint = "";

        List<Diagnostic<? extends JavaFileObject>> diagnosticsList = diagnostica.getDiagnostics();
        if (!diagnosticsList.isEmpty() && classNamesBeingCompiled != null && classNamesBeingCompiled.length > 0) {
            // Tenta di identificare se l'errore è più probabile nel codice utente o nel test
            // classNamesBeingCompiled[0] è ipotizzato essere la classe utente
            // classNamesBeingCompiled[1] (se esiste) è ipotizzato essere la classe test
            boolean errorLikelyInUserCode = false;
            boolean errorLikelyInTestCode = false;
            String userClassNameForError = classNamesBeingCompiled[0];
            String testClassNameForError = (classNamesBeingCompiled.length > 1) ? classNamesBeingCompiled[1] : null;

            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticsList) {
                if (diagnostic.getSource() != null) {
                    String sourceName = diagnostic.getSource().getName();
                    if (sourceName.endsWith("/" + userClassNameForError + ".java")) {
                        errorLikelyInUserCode = true;
                    }
                    if (testClassNameForError != null && sourceName.endsWith("/" + testClassNameForError + ".java")) {
                        errorLikelyInTestCode = true;
                    }
                }
            }
            if (errorLikelyInUserCode && !errorLikelyInTestCode) editorHint = " (nel tuo codice)";
            else if (errorLikelyInTestCode && !errorLikelyInUserCode) editorHint = " (nel codice di test)";
            // Se entrambi o nessuno, non aggiunge hint specifico nel titolo
        }
        messaggioErroreHtml.append(editorHint).append(":</h3><ul>");
        
        if (diagnosticsList.isEmpty()) {
             messaggioErroreHtml.append("<li>Nessun dettaglio specifico sull'errore di compilazione disponibile.</li>");
        } else {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticsList) {
                long lineNumber = diagnostic.getLineNumber();
                if (lineNumber < 0) lineNumber = 1; // Alcuni errori potrebbero non avere un numero di riga specifico

                String rawMessage = diagnostic.getMessage(null); // Locale.getDefault()
                String messaggioNonNull = (rawMessage != null && !rawMessage.trim().isEmpty()) ? rawMessage : "Errore di compilazione non specificato.";
                String escapedHtmlMessage = StringEscapeUtils.escapeHtml4(messaggioNonNull);
                String escapedJsMessage = StringEscapeUtils.escapeEcmaScript(messaggioNonNull);
                
                String editorTarget = "editor"; // Default per codice utente
                if (diagnostic.getSource() != null && classNamesBeingCompiled != null && classNamesBeingCompiled.length > 1) {
                    String sourceName = diagnostic.getSource().getName();
                    // classNamesBeingCompiled[0] è utente, classNamesBeingCompiled[1] è test
                    if (sourceName.endsWith("/" + classNamesBeingCompiled[1] + ".java")) {
                        editorTarget = "testEditor";
                    }
                }

                messaggioErroreHtml.append("<li>")
                    .append("<a href='#' onclick='event.preventDefault(); handleErrorClick(\"").append(editorTarget).append("\", ") 
                    .append(lineNumber).append(", \"").append(escapedJsMessage).append("\", \"error\");' title='Clicca per evidenziare la riga nell&#39;editor مربوطه'>")
                    .append("<b>Errore riga ").append(lineNumber).append(":</b> ")
                    .append(escapedHtmlMessage)
                    .append("</a></li>");
            }
        }
        messaggioErroreHtml.append("</ul></div>");
        return messaggioErroreHtml.toString();
    }
    
    // --- Metodi per esecuzione Test JUnit e formattazione fallimenti ---
    // (Questi rimangono identici alla versione precedente "Soluzione 1")
    public static class TestResult { 
        public final boolean success; public final List<Map<String, String>> failures;
        public TestResult(boolean success, List<Map<String, String>> failures){ this.success = success; this.failures = failures; }
    }

    public TestResult eseguiTestJUnit(Class<?> classeUtente, Class<?> classeTest) throws Exception {
        logger.debug("Esecuzione test JUnit per utente: {} e test: {}", classeUtente.getName(), classeTest.getName());
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(classeTest))
                .build();
        Launcher launcher = LauncherFactory.create();
        
        // Salva il classloader originale e imposta quello che ha caricato le classi di test
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classeTest.getClassLoader());
            launcher.execute(request, listener);
        } finally {
            // Ripristina il classloader originale
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }

        TestExecutionSummary summary = listener.getSummary();
        List<Map<String, String>> risultatiTest = new ArrayList<>();

        if (summary.getTestsFailedCount() > 0) {
            logger.warn("{} test falliti su {} eseguiti.", summary.getTestsFailedCount(), summary.getTestsStartedCount());
        }

        for (Failure failure : summary.getFailures()) {
            Map<String, String> risultato = new HashMap<>();
            String testDisplayName = failure.getTestIdentifier().getDisplayName();
            Throwable exception = failure.getException();
            String rawErrorMessage = (exception != null) ? exception.getMessage() : "Nessun messaggio di errore specifico per il fallimento del test.";
            if (rawErrorMessage == null && exception != null) rawErrorMessage = exception.getClass().getSimpleName(); // Fallback al nome dell'eccezione

            String stackTrace = getStackTraceAsString(exception);
            int lineNumber = extractLineNumberFromError(stackTrace, classeTest.getName()); // Nome completo della classe test

            risultato.put("testName", testDisplayName);
            risultato.put("errorMessage", rawErrorMessage);
            risultato.put("lineNumber", String.valueOf(lineNumber));
            risultatiTest.add(risultato);
            logger.debug("Fallimento Test: '{}', Errore: '{}', Riga: {}", testDisplayName, rawErrorMessage, lineNumber);
        }
        boolean successOverall = summary.getFailures().isEmpty();
        return new TestResult(successOverall, risultatiTest);
    }
    
    private String formattaFallimentiTestHtml(List<Map<String, String>> failures) {
        if (failures == null || failures.isEmpty()) {
            return ""; // Non dovrebbe accadere se chiamato da testFailure
        }
        StringBuilder outputMessaggio = new StringBuilder();
        outputMessaggio.append("<div class='errore'><h3>Test Falliti:</h3><ul>");

        for (Map<String, String> risultato : failures) {
            String testName = risultato.getOrDefault("testName", "Test Sconosciuto");
            String errorMessage = risultato.getOrDefault("errorMessage", "Nessun dettaglio sul fallimento.");
            int lineNumber = 1; // Default
            try {
                 lineNumber = Integer.parseInt(risultato.getOrDefault("lineNumber", "1"));
                 if (lineNumber < 1) lineNumber = 1; 
            } catch (NumberFormatException nfe) { /* usa default 1 */ }

            String escapedHtmlTestName = StringEscapeUtils.escapeHtml4(testName);
            String escapedHtmlErrorMessage = StringEscapeUtils.escapeHtml4(errorMessage);
            String jsMessage = String.format("Test fallito: %s. Dettagli: %s",
                                              StringEscapeUtils.escapeEcmaScript(testName),
                                              StringEscapeUtils.escapeEcmaScript(errorMessage));

            outputMessaggio.append("<li>")
                .append("<a href='#' onclick='event.preventDefault(); handleErrorClick(\"testEditor\", ")
                .append(lineNumber).append(", \"").append(jsMessage).append("\", \"error\");' title='Clicca per evidenziare la riga nel codice di test'>")
                .append("Test: ").append(escapedHtmlTestName).append(" (fallito vicino alla riga ~").append(lineNumber).append(" del file di test)")
                .append("</a><br><b>Dettaglio Fallimento:</b> ")
                .append(escapedHtmlErrorMessage)
                .append("</li>");
        }
        outputMessaggio.append("</ul></div>");
        return outputMessaggio.toString();
    }

    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) return "";
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw); return sw.toString();
        } catch (Exception e) { 
            logger.warn("Errore durante la conversione dello stack trace in stringa.", e);
            return "Non è stato possibile ottenere lo stack trace dell'errore.";
        }
    }

    private int extractLineNumberFromError(String stackTrace, String nomeClasseTestCompleto) {
         if (stackTrace == null || stackTrace.isEmpty() || nomeClasseTestCompleto == null || nomeClasseTestCompleto.trim().isEmpty()) return 1;
         int numeroRiga = 1; 
         
         String nomeFileTestSemplice;
         int lastDot = nomeClasseTestCompleto.lastIndexOf('.');
         if (lastDot != -1 && lastDot < nomeClasseTestCompleto.length() - 1) {
            nomeFileTestSemplice = nomeClasseTestCompleto.substring(lastDot + 1) + ".java";
         } else {
            nomeFileTestSemplice = nomeClasseTestCompleto + ".java"; // Se non c'è package
         }
         
         // Pattern per cercare: NomeClasseTest.java:NUMERO_RIGA
         Pattern stackTracePattern = Pattern.compile(Pattern.quote(nomeFileTestSemplice) + ":(\\d+)");
         Matcher matcher = stackTracePattern.matcher(stackTrace);
         
         if (matcher.find()) { // Trova la prima occorrenza che è spesso la più rilevante
             try { 
                 numeroRiga = Integer.parseInt(matcher.group(1));
             } catch (NumberFormatException e) { 
                 logger.warn("Impossibile parsare il numero di riga '{}' dallo stack trace per il file {}", matcher.group(1), nomeFileTestSemplice);
                 numeroRiga = 1; // Fallback
             }
         } else {
             // Fallback generico se il nome specifico del file di test non viene trovato.
             // Cerca la prima occorrenza di un qualsiasi (FileName.java:line)
             Pattern genericPattern = Pattern.compile("\\((\\w+\\.java):(\\d+)\\)");
             Matcher genericMatcher = genericPattern.matcher(stackTrace);
             if (genericMatcher.find()) {
                  try { numeroRiga = Integer.parseInt(genericMatcher.group(2)); } 
                  catch (NumberFormatException e) { numeroRiga = 1; }
             }
         }
         return (numeroRiga > 0) ? numeroRiga : 1; 
     }

    // --- Classi interne per compilazione in memoria ---
    static class CodiceSorgenteStringa extends SimpleJavaFileObject {
        private final String codice;
        protected CodiceSorgenteStringa(String nomeClasse, String codice) {
            // Assicura che il nome della classe sia un nome file valido per URI
            super(URI.create("string:///" + nomeClasse.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.codice = codice;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return codice; }
    }

    private static class GestoreCompilazioneInMemoria extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, BytecodeOutput> bytecodeMap = new HashMap<>();
        protected GestoreCompilazioneInMemoria(JavaFileManager fileManager) { super(fileManager); }
        
        @Override 
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
            // className è il nome FQN, es. com.example.MyClass
            BytecodeOutput output = new BytecodeOutput(className); // Usa il FQN per la mappa
            bytecodeMap.put(className, output);
            return output;
        }
        
        public Map<String, byte[]> getAllBytecodes() {
            Map<String, byte[]> result = new HashMap<>();
            bytecodeMap.forEach((className, output) -> result.put(className, output.getBytes()));
            return result;
        }
    }

    private static class BytecodeOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // Il nome qui è per l'URI, usa il FQN
        protected BytecodeOutput(String fqnClassName) {
            super(URI.create("bytes:///" + fqnClassName.replace('.', '/')), Kind.CLASS);
        }
        @Override public OutputStream openOutputStream() { return outputStream; }
        public byte[] getBytes() { return outputStream.toByteArray(); }
    }

    private static class CaricatoreClassiInMemoria extends ClassLoader {
        private final Map<String, byte[]> classBytecodes; // Mappa FQN -> bytecode
        public CaricatoreClassiInMemoria(Map<String, byte[]> bytecodes, ClassLoader parent) {
            super(parent);
            // Crea una copia difensiva della mappa
            this.classBytecodes = (bytecodes != null) ? new HashMap<>(bytecodes) : new HashMap<>();
        }
        
        @Override 
        protected Class<?> findClass(String fqnClassName) throws ClassNotFoundException {
            byte[] bytes = classBytecodes.get(fqnClassName);
            if (bytes == null) {
                // Se non troviamo i bytecode nella nostra mappa, deleghiamo al parent class loader
                // Questo è importante per caricare classi di sistema, librerie (es. JUnit), ecc.
                logger.trace("Classe '{}' non trovata in CaricatoreClassiInMemoria, delega al parent.", fqnClassName);
                return super.findClass(fqnClassName); 
            }
            logger.trace("Definizione classe '{}' da bytecode in CaricatoreClassiInMemoria.", fqnClassName);
            // Definisce la classe usando i byte forniti
            return defineClass(fqnClassName, bytes, 0, bytes.length);
        }
    }
}