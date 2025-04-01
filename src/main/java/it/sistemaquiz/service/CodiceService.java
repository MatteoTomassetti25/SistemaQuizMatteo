package it.sistemaquiz.service;



import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;
import org.springframework.stereotype.Service;

import it.sistemaquiz.model.JSONResponse;

@Service
public class CodiceService {

    private boolean output = false;

    public boolean getOutput() {
        return this.output;
    }

    public JSONResponse compila(String codiceUtente) {

        JSONResponse rispostaJSON = new JSONResponse();

        try {
            String nomeClasse = estraiNomeClasse(codiceUtente);
            if (nomeClasse == null) {
                rispostaJSON.setStatus("Errore");
                rispostaJSON.setMessaggio("Dichiarazione classe non valida.");
                return rispostaJSON;
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavaFileObject file = new CodiceUtenteStringa(nomeClasse, codiceUtente);
            DiagnosticCollector<JavaFileObject> diagnostica = new DiagnosticCollector<>();
            GestoreCompilazioneInMemoria fileManager = new GestoreCompilazioneInMemoria(
                    compiler.getStandardFileManager(null, null, null));

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostica, null, null,
                    List.of(file));
            boolean riuscita = task.call();

            if (!riuscita) {
                StringBuilder messaggioErrore = new StringBuilder();
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostica.getDiagnostics()) {
                    messaggioErrore.append("Errore alla riga ").append(diagnostic.getLineNumber())
                            .append(" nella classe ").append(diagnostic.getSource().getName()).append(": ")
                            .append(diagnostic.getMessage(null)).append("\n");
                }
                rispostaJSON.setStatus("Errore");
                rispostaJSON.setMessaggio(messaggioErrore.toString());
                return rispostaJSON;
            }

            rispostaJSON.setStatus("Successo");
            rispostaJSON.setMessaggio("Compilazione riuscita.");
            return rispostaJSON;

        } catch (Exception e) {

            rispostaJSON.setStatus("Errore");
            rispostaJSON.setMessaggio("Errore durante compilazione: " + e.getMessage());
            return rispostaJSON;

        }

    }

    public String estraiNomeClasse(String codice) {

        // JSONResponse rispostaJson = new JSONResponse();

        String keyword = "class ";
        int index = codice.indexOf(keyword);
        if (codice.length() < 5) {
            return null;
        }
        if (index != -1) {
            int startIndex = index + keyword.length();
            int endIndex = codice.indexOf(" ", startIndex);
            if (endIndex == -1) {
                endIndex = codice.indexOf("{", startIndex);
            }
            if (endIndex != -1) {
                return codice.substring(startIndex, endIndex).trim();
            }
        }

        return null;

    }

    public Map<String, Class<?>> caricaClassiCompilate(Map<String, String> codiceClassi) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostica = new DiagnosticCollector<>();
        GestoreCompilazioneInMemoria gestoreCompilazioneInMemoria = new GestoreCompilazioneInMemoria(
                compiler.getStandardFileManager(null, null, null));

        List<JavaFileObject> files = new ArrayList<>();
        for (Map.Entry<String, String> entry : codiceClassi.entrySet()) {
            String nomeClasse = entry.getKey();
            String codice = entry.getValue();
            files.add(new CodiceUtenteStringa(nomeClasse, codice));
        }

        JavaCompiler.CompilationTask task = compiler.getTask(null, gestoreCompilazioneInMemoria, diagnostica, null,
                null, files);
        boolean riuscita = task.call();

        if (!riuscita) {
            StringBuilder messaggioErrore = new StringBuilder("<div>Errore di compilazione:<ul>");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostica.getDiagnostics()) {
                messaggioErrore.append("<li><a href='#line")
                        .append(diagnostic.getLineNumber())
                        .append("'>Errore alla riga ")
                        .append(diagnostic.getLineNumber())
                        .append("</a>: ")
                        .append(diagnostic.getMessage(null))
                        .append("</li>");
            }
            messaggioErrore.append("</ul></div>");

            throw new IllegalStateException(messaggioErrore.toString());

        }

        Map<String, byte[]> classBytes = gestoreCompilazioneInMemoria.getBytes();
        CaricatoreClassiInMemoria classLoader = new CaricatoreClassiInMemoria(classBytes);

        Map<String, Class<?>> classiCompilate = new HashMap<>();
        for (String nomeClasse : codiceClassi.keySet()) {
            classiCompilate.put(nomeClasse, classLoader.loadClass(nomeClasse));
        }

        return classiCompilate;
    }

    public List<Map<String, String>> eseguiTestJUnit(Class<?> classeUtente, Class<?> classeTest) throws Exception {

        this.output = false;

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(classeTest))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        List<Map<String, String>> risultatiTest = new ArrayList<>();

        for (Failure i : summary.getFailures()) {
            Map<String, String> risultato = new HashMap<>();

            // Nome del test in una riga separata
            risultato.put("test", " <b></b> " + i.getTestIdentifier().getDisplayName() + "<br>");

            // Messaggio di errore con rientro e nuova riga
            risultato.put("errore", " <i></i> " + i.getException().getMessage() + "<br><br>");

            risultatiTest.add(risultato);
        }

        if (summary.getFailures().size() == 0)
            this.output = true;

        return risultatiTest;
    }

    class CodiceUtenteStringa extends SimpleJavaFileObject {
        private final String codice;

        protected CodiceUtenteStringa(String nomeClasse, String codice) {
            super(URI.create("string:///" + nomeClasse + Kind.SOURCE.extension), Kind.SOURCE);
            this.codice = codice;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return codice;
        }
    }

    private static class GestoreCompilazioneInMemoria extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, Bytecode> bytecodeMap = new HashMap<>();

        protected GestoreCompilazioneInMemoria(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                FileObject sibling) {
            Bytecode bytecode = new Bytecode(className);
            bytecodeMap.put(className, bytecode);
            return bytecode;
        }

        public Map<String, byte[]> getBytes() {
            Map<String, byte[]> result = new HashMap<>();
            for (Map.Entry<String, Bytecode> entry : bytecodeMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getBytes());
            }
            return result;
        }
    }

    private static class Bytecode extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        protected Bytecode(String name) {
            super(URI.create("bytes:///" + name), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    private static class CaricatoreClassiInMemoria extends ClassLoader {
        private final Map<String, byte[]> classBytes;

        public CaricatoreClassiInMemoria(Map<String, byte[]> classBytes) {
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

   
    

}
