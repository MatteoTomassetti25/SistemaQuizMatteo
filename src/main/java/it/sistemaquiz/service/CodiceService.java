package it.sistemaquiz.service; // Assicurati che il package sia corretto

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;
// Rimosso import logger
import org.springframework.stereotype.Service;

import it.sistemaquiz.entity.Domanda;


@Service
public class CodiceService {

    // Rimosso campo logger

    public static class ExecutionResult {
        public final boolean overallSuccess;
        public final String htmlBody;
        public final boolean compilationError;

        public ExecutionResult(boolean overallSuccess, String htmlBody, boolean compilationError) {
            this.overallSuccess = overallSuccess;
            this.htmlBody = htmlBody;
            this.compilationError = compilationError;
        }
        public static ExecutionResult success() {
            return new ExecutionResult(true, "<div class='successo'>TEST PASSATI CON SUCCESSO!</div>", false);
        }
        public static ExecutionResult compilationError(String htmlErrorBody) {
            return new ExecutionResult(false, htmlErrorBody, true);
        }
        public static ExecutionResult testFailure(String htmlFailureBody) {
            return new ExecutionResult(false, htmlFailureBody, false);
        }
         public static ExecutionResult internalError(String message) {
             String htmlBody = String.format("<div class='errore'>Errore interno: %s</div>", StringEscapeUtils.escapeHtml4(message));
            return new ExecutionResult(false, htmlBody, false);
        }
    }

    public ExecutionResult eseguiCompilazioneETest(Domanda domanda, String codiceSorgente) {
        String testSorgente = domanda.getTest();

        if (testSorgente == null || testSorgente.trim().isEmpty()) {
             return ExecutionResult.internalError("Codice di test non disponibile per la domanda.");
        }

        String nomeClassePrincipale = estraiNomeClasse(codiceSorgente);
        String nomeClasseTest = estraiNomeClasse(testSorgente);

        if (nomeClassePrincipale == null) {
            return ExecutionResult.internalError("Impossibile determinare il nome della classe principale dal codice fornito.");
        }
        if (nomeClasseTest == null) {
             return ExecutionResult.internalError("Impossibile determinare il nome della classe di test.");
        }

        String placeholder = "CodiceUtente";
        String codiceTestAggiornato = testSorgente;
        if (testSorgente.contains(placeholder)) {
             codiceTestAggiornato = testSorgente.replace(placeholder, nomeClassePrincipale);
        }

        Map<String, String> codiceClassi = new HashMap<>();
        codiceClassi.put(nomeClassePrincipale, codiceSorgente);
        codiceClassi.put(nomeClasseTest, codiceTestAggiornato);

        try {
            Map<String, Class<?>> classiCompilate = caricaClassiCompilate(codiceClassi);
            Class<?> classeUtente = classiCompilate.get(nomeClassePrincipale);
            Class<?> classeTest = classiCompilate.get(nomeClasseTest);

            if (classeUtente == null || classeTest == null) {
                 return ExecutionResult.internalError("Errore interno durante il caricamento delle classi compilate.");
            }

            TestResult testResult = eseguiTestJUnit(classeUtente, classeTest);

            if (testResult.success) {
                return ExecutionResult.success();
            } else {
                String htmlFailureBody = formattaFallimentiTestHtml(testResult.failures);
                return ExecutionResult.testFailure(htmlFailureBody);
            }

        } catch (IllegalStateException e) { // Errore di Compilazione
            return ExecutionResult.compilationError(e.getMessage());
        } catch (Exception e) { // Altri Errori Inattesi
            return ExecutionResult.internalError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String estraiNomeClasse(String codice) {
        if (codice == null) return null;
        Pattern pattern = Pattern.compile("\\b(?:public\\s+)?class\\s+([a-zA-Z_$][a-zA-Z\\d_$]*)\\s*(?:<[^>]+>)?(?:extends\\s+\\S+)?(?:implements\\s+\\S+(?:,\\s*\\S+)*)?\\s*\\{");
        Matcher matcher = pattern.matcher(codice);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null; // Non trovato
    }

    public Map<String, Class<?>> caricaClassiCompilate(Map<String, String> codiceClassi) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
             throw new RuntimeException("JavaCompiler di sistema non disponibile.");
        }

        DiagnosticCollector<JavaFileObject> diagnostica = new DiagnosticCollector<>();
        Map<String, Class<?>> classiCompilate = new HashMap<>();

        try (JavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostica, null, null)) {
            if (standardFileManager == null) {
                 throw new RuntimeException("Java Standard File Manager non trovato.");
            }

            GestoreCompilazioneInMemoria gestoreCompilazioneInMemoria = new GestoreCompilazioneInMemoria(standardFileManager);
            List<JavaFileObject> files = new ArrayList<>();
            for (Map.Entry<String, String> entry : codiceClassi.entrySet()) {
                String nomeClasse = entry.getKey();
                if (nomeClasse == null || nomeClasse.trim().isEmpty() || !nomeClasse.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*")) {
                    throw new IllegalArgumentException("Nome classe non valido rilevato: '" + nomeClasse + "'");
                }
                String codice = entry.getValue();
                files.add(new CodiceUtenteStringa(nomeClasse, codice));
            }

            if (files.isEmpty()) {
                return classiCompilate;
            }

            JavaCompiler.CompilationTask task = compiler.getTask(null, gestoreCompilazioneInMemoria, diagnostica, null, null, files);
            boolean riuscita = task.call();

            if (!riuscita) {
                String htmlErrorBody = formattaErroriCompilazioneHtml(diagnostica);
                throw new IllegalStateException(htmlErrorBody);
            }

            Map<String, byte[]> classBytes = gestoreCompilazioneInMemoria.getBytes();
            ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
             if (parentClassLoader == null) parentClassLoader = ClassLoader.getSystemClassLoader();

            CaricatoreClassiInMemoria classLoader = new CaricatoreClassiInMemoria(classBytes, parentClassLoader);
            for (String nomeClasse : codiceClassi.keySet()) {
                 if (nomeClasse != null && !nomeClasse.trim().isEmpty()) {
                     classiCompilate.put(nomeClasse, classLoader.loadClass(nomeClasse));
                 }
            }
        } catch (IOException e) {
            throw new RuntimeException("Errore I/O durante la compilazione.", e);
        }
        return classiCompilate;
    }

    private String formattaErroriCompilazioneHtml(DiagnosticCollector<JavaFileObject> diagnostica) {
        StringBuilder messaggioErroreHtml = new StringBuilder("<div class='errore'><h3>Errore di Compilazione:</h3><ul>");
        StringBuilder scriptErrori = new StringBuilder("<script>");
        boolean firstError = true;

        List<Diagnostic<? extends JavaFileObject>> diagnosticsList = diagnostica.getDiagnostics();

        if (diagnosticsList.isEmpty()) {
             // Caso improbabile, ma gestiamolo
             messaggioErroreHtml.append("<li>Nessun dettaglio sull'errore di compilazione disponibile.</li>");
        } else {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticsList) {
                // Consideriamo solo errori reali e non note o warning, se necessario
                // if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {

                    long lineNumber = diagnostic.getLineNumber();
                    // Assicuriamoci che lineNumber sia valido per Ace (>= 1)
                    if (lineNumber < 1) lineNumber = 1;

                    String rawMessage = diagnostic.getMessage(null);
                    String messaggioNonNull = (rawMessage != null) ? rawMessage : "Errore sconosciuto";
                    String escapedJsMessage = StringEscapeUtils.escapeEcmaScript(messaggioNonNull); // Per JS
                    String escapedHtmlMessage = StringEscapeUtils.escapeHtml4(messaggioNonNull); // Per HTML

                    // Aggiungi l'errore alla lista HTML (senza link ora)
                    messaggioErroreHtml.append("<li>")
                        .append("<b>Errore riga ").append(lineNumber).append(":</b> ")
                        .append(escapedHtmlMessage)
                        .append("</li>");

                    // Aggiungi la chiamata alla funzione JS esistente per questo errore
                    // Nota: passiamo 'editor' come primo argomento per indicare l'editor principale
                    scriptErrori.append("handleErrorClick('editor', ")
                        .append(lineNumber).append(", \"").append(escapedJsMessage).append("\", \"error\");");

                    // Se è il primo errore, potremmo voler scrollare fino a quella linea
                    if (firstError) {
                        // Aggiungiamo uno scroll e focus sul primo errore dopo un piccolo ritardo
                        // per dare tempo ad Ace e HTMX di sistemare il DOM.
                        scriptErrori.append("setTimeout(function() { try { window.editor.scrollToLine(")
                                  .append(lineNumber)
                                  .append(", true, true, function(){}); window.editor.gotoLine(")
                                  .append(lineNumber)
                                  .append(", 0, true); window.editor.focus(); } catch(e){ console.error('Error scrolling to first error:', e); } }, 100);");
                        firstError = false;
                    }
                // } // Fine if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
            }
        }

        messaggioErroreHtml.append("</ul></div>");
        scriptErrori.append("</script>");

        // Aggiungi lo script generato alla fine dell'HTML
        messaggioErroreHtml.append(scriptErrori);

        return messaggioErroreHtml.toString();
    }

    /**
     * Classe interna (o record se usi Java >= 16) per contenere i risultati dei test.
     */
    public static class TestResult {
        public final boolean success;
        public final List<Map<String, String>> failures;
        public TestResult(boolean success, List<Map<String, String>> failures) {
            this.success = success;
            this.failures = failures;
        }
    }

    public TestResult eseguiTestJUnit(Class<?> classeUtente, Class<?> classeTest) throws Exception {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(classeTest))
                .build();
        Launcher launcher = LauncherFactory.create();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classeTest.getClassLoader());
            launcher.execute(request, listener);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

        TestExecutionSummary summary = listener.getSummary();
        List<Map<String, String>> risultatiTest = new ArrayList<>();

        for (Failure failure : summary.getFailures()) {
            Map<String, String> risultato = new HashMap<>();
            String testDisplayName = failure.getTestIdentifier().getDisplayName();
            Throwable exception = failure.getException();
            String rawErrorMessage = (exception != null) ? exception.toString() : "Nessun dettaglio";
            String stackTrace = getStackTraceAsString(exception);
            int lineNumber = extractLineNumberFromError(stackTrace, classeTest.getName());

            risultato.put("testName", testDisplayName);
            risultato.put("errorMessage", rawErrorMessage);
            risultato.put("lineNumber", String.valueOf(lineNumber));
            risultatiTest.add(risultato);
        }
        boolean success = summary.getFailures().isEmpty();
        return new TestResult(success, risultatiTest);
    }

    private String formattaFallimentiTestHtml(List<Map<String, String>> failures) {
        StringBuilder outputMessaggio = new StringBuilder();
        outputMessaggio.append("<div class='errore'><h3>Test Falliti:</h3><ul>");

        for (Map<String, String> risultato : failures) {
            String testName = risultato.getOrDefault("testName", "Test Sconosciuto");
            String errorMessage = risultato.getOrDefault("errorMessage", "Nessun dettaglio");
            int lineNumber = 1;
            try {
                 lineNumber = Integer.parseInt(risultato.getOrDefault("lineNumber", "1"));
                 if (lineNumber < 1) lineNumber = 1;
            } catch (NumberFormatException nfe) { lineNumber = 1; }

            String escapedHtmlTestName = StringEscapeUtils.escapeHtml4(testName);
            String escapedHtmlErrorMessage = StringEscapeUtils.escapeHtml4(errorMessage);
            String jsMessage = String.format("Test: %s | Dettagli: %s",
                                              StringEscapeUtils.escapeEcmaScript(testName),
                                              StringEscapeUtils.escapeEcmaScript(errorMessage));

            outputMessaggio.append("<li>")
                .append("<a href='#' onclick='handleErrorClick(\"testEditor\", ")
                .append(lineNumber).append(", \"").append(jsMessage).append("\", \"error\"); return false;'>")
                .append("Test: ").append(escapedHtmlTestName).append(" (riga ~").append(lineNumber).append(")")
                .append("</a><br><b>Dettagli:</b> ")
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
        } catch (Exception e) { return "Errore stack trace."; }
    }

    private int extractLineNumberFromError(String stackTrace, String nomeClasseTest) {
         if (stackTrace == null || stackTrace.isEmpty()) return 1;
         int numeroRiga = 1;
         String nomeFileTest = nomeClasseTest.substring(nomeClasseTest.lastIndexOf('.') + 1) + ".java";
          Pattern stackTracePattern = Pattern.compile("\\(\\s*" + Pattern.quote(nomeFileTest) + ":(\\d+)\\s*\\)");
          Matcher matcher = stackTracePattern.matcher(stackTrace);
         if (matcher.find()) {
             try { numeroRiga = Integer.parseInt(matcher.group(1)); } catch (NumberFormatException e) { numeroRiga = 1; }
         } else {
              Pattern genericPattern = Pattern.compile("\\((\\w+\\.java):(\\d+)\\)");
              Matcher genericMatcher = genericPattern.matcher(stackTrace);
              int lastLineNumber = -1;
              while (genericMatcher.find()) {
                  try { lastLineNumber = Integer.parseInt(genericMatcher.group(2)); } catch (NumberFormatException e) { /* ignore */ }
              }
              if (lastLineNumber > 0) numeroRiga = lastLineNumber;
         }
         return (numeroRiga > 0) ? numeroRiga : 1;
     }

    // --- Classi interne per compilazione in memoria (Invariate) ---
    static class CodiceUtenteStringa extends SimpleJavaFileObject {
        private final String codice;
        protected CodiceUtenteStringa(String nomeClasse, String codice) {
            super(URI.create("string:///" + nomeClasse.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE); this.codice = codice;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return codice; }
    }
    private static class GestoreCompilazioneInMemoria extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, Bytecode> bytecodeMap = new HashMap<>();
        protected GestoreCompilazioneInMemoria(JavaFileManager fileManager) { super(fileManager); }
        @Override public JavaFileObject getJavaFileForOutput(Location l, String c, JavaFileObject.Kind k, FileObject s) {
            Bytecode b = new Bytecode(c); bytecodeMap.put(c, b); return b;
        }
        public Map<String, byte[]> getBytes() {
            Map<String, byte[]> r = new HashMap<>(); bytecodeMap.forEach((n, b) -> r.put(n, b.getBytes())); return r;
        }
    }
    private static class Bytecode extends SimpleJavaFileObject {
        private final ByteArrayOutputStream o = new ByteArrayOutputStream();
        protected Bytecode(String n) { super(URI.create("bytes:///" + n.replace('.', '/')), Kind.CLASS); }
        @Override public OutputStream openOutputStream() { return o; }
        public byte[] getBytes() { return o.toByteArray(); }
    }
    private static class CaricatoreClassiInMemoria extends ClassLoader {
        private final Map<String, byte[]> cb;
        public CaricatoreClassiInMemoria(Map<String, byte[]> b, ClassLoader p) { super(p); this.cb = b; }
        @Override protected Class<?> findClass(String n) throws ClassNotFoundException {
            byte[] bytes = cb.get(n); if (bytes == null) return super.findClass(n);
            return defineClass(n, bytes, 0, bytes.length);
        }
    }
}