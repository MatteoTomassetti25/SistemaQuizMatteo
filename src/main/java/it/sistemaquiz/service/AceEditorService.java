package it.sistemaquiz.service;

import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Servizio per l'integrazione tra Java e Ace Editor che fornisce metodi per interagire
 * con le funzionalità di Ace Editor tramite codice Java, generando script JavaScript
 * che vengono poi eseguiti nel browser.
 * 
 * Questo service astrae le complessità dell'API di Ace Editor, permettendo di manipolare
 * l'editor da Java in modo dichiarativo.
 * 
 * Documentazione Ace Editor API: https://ajaxorg.github.io/ace-api-docs/index.html
 */
@Service
public class AceEditorService {

    /**
     * Crea uno script per inizializzare un'istanza di Ace Editor con configurazioni di base.
     * 
     * @param editorId L'ID del div HTML che conterrà l'editor
     * @param mode Il mode di sintassi da usare (es. "java", "javascript")
     * @param theme Il tema da applicare (es. "eclipse", "monokai")
     * @param readOnly Se l'editor deve essere in sola lettura
     * @return Script JS per l'inizializzazione dell'editor
     * 
     * @see Ace Editor API: ace.edit()
     */
    public String createInitScript(String editorId, String mode, String theme, boolean readOnly) {
        return String.format(
            "var %1$s = ace.edit('%1$s'); " +
            "%1$s.setTheme('ace/theme/%2$s'); " +
            "%1$s.session.setMode('ace/mode/%3$s'); " +
            "%1$s.setReadOnly(%4$b); " +
            "%1$s.setOptions({ " +
            "   enableBasicAutocompletion: true, " +
            "   enableSnippets: true, " +
            "   enableLiveAutocompletion: true " +
            "});",
            editorId, theme, mode, readOnly
        );
    }

    /**
     * Crea uno script per impostare il contenuto dell'editor.
     * 
     * @param editorId L'ID dell'editor da modificare
     * @param content Il contenuto da impostare
     * @return Script JS per impostare il valore
     * 
     * @see Ace Editor API: Editor.setValue()
     */
    public String createSetContentScript(String editorId, String content) {
        // Escape i caratteri speciali JavaScript
        String escapedContent = content.replace("\\", "\\\\")
                                     .replace("'", "\\'")
                                     .replace("\n", "\\n")
                                     .replace("\r", "\\r")
                                     .replace("\t", "\\t");
        
        return String.format(
            "%s.setValue('%s'); %s.clearSelection();",
            editorId, escapedContent, editorId
        );
    }

    /**
     * Crea uno script per aggiungere un marker di errore a una specifica riga.
     * 
     * @param editorId L'ID dell'editor
     * @param lineNum Il numero di riga (1-based)
     * @param message Il messaggio da mostrare
     * @return Script JS per aggiungere l'annotazione
     * 
     * @see Ace Editor API: Annotation, Session.setAnnotations()
     */
    public String createErrorMarkerScript(String editorId, int lineNum, String message) {
        return String.format(
            "%1$s.session.setAnnotations([{ " +
            "   row: %2$d, " +  // row è 0-based
            "   column: 0, " +
            "   text: '%3$s', " +
            "   type: 'error' " +
            "}]); " +
            "%1$s.scrollToLine(%2$d, true, true); " +
            "%1$s.gotoLine(%4$d, 0, true);",
            editorId, lineNum - 1, message, lineNum
        );
    }

    /**
     * Crea uno script per aggiungere un marker personalizzato all'editor.
     * 
     * @param editorId L'ID dell'editor
     * @param lineNum Il numero di riga (1-based)
     * @param message Il messaggio da mostrare
     * @param type Il tipo di annotazione (error, warning, info)
     * @return Script JS per aggiungere l'annotazione
     * 
     * @see Ace Editor API: Annotation
     */
    public String createMarkerScript(String editorId, int lineNum, String message, String type) {
        return String.format(
            "%1$s.session.setAnnotations([{ " +
            "   row: %2$d, " +
            "   column: 0, " +
            "   text: '%3$s', " +
            "   type: '%4$s' " +
            "}]);",
            editorId, lineNum - 1, message, type
        );
    }

    /**
     * Crea uno script per pulire tutte le annotazioni/marker dall'editor.
     * 
     * @param editorId L'ID dell'editor
     * @return Script JS per pulire le annotazioni
     * 
     * @see Ace Editor API: Session.clearAnnotations()
     */
    public String createClearMarkersScript(String editorId) {
        return String.format(
            "%s.session.clearAnnotations(); " +
            "Array.from(document.querySelectorAll('.ace_marker')).forEach(m => m.remove());",
            editorId
        );
    }

    /**
     * Crea uno script per evidenziare una specifica riga nell'editor.
     * 
     * @param editorId L'ID dell'editor
     * @param lineNum Il numero di riga (1-based)
     * @param highlightClass La classe CSS da applicare
     * @return Script JS per l'evidenziazione
     * 
     * @see Ace Editor API: Session.addMarker()
     */
    public String createHighlightLineScript(String editorId, int lineNum, String highlightClass) {
        return String.format(
            "if (window.%1$sMarker) { %1$s.session.removeMarker(window.%1$sMarker); } " +
            "window.%1$sMarker = %1$s.session.addMarker( " +
            "   new Range(%2$d, 0, %2$d, Infinity), " +
            "   '%3$s', " +
            "   'fullLine' " +
            ");",
            editorId, lineNum - 1, highlightClass
        );
    }

    /**
     * Crea uno script per impostare opzioni aggiuntive sull'editor.
     * 
     * @param editorId L'ID dell'editor
     * @param options Mappa di opzioni (chiave-valore)
     * @return Script JS per impostare le opzioni
     * 
     * @see Ace Editor API: Editor.setOptions()
     */
    public String createSetOptionsScript(String editorId, Map<String, Object> options) {
        StringBuilder optionsStr = new StringBuilder();
        options.forEach((key, value) -> {
            optionsStr.append(key).append(": ");
            if (value instanceof String) {
                optionsStr.append("'").append(value).append("'");
            } else {
                optionsStr.append(value);
            }
            optionsStr.append(", ");
        });

        return String.format("%s.setOptions({ %s });", editorId, optionsStr.toString());
    }

    /**
     * Crea uno script per aggiungere un marker personalizzato con stile CSS.
     * 
     * @param editorId L'ID dell'editor
     * @param lineNum Il numero di riga (1-based)
     * @param cssClass La classe CSS da applicare al marker
     * @param content Il contenuto da mostrare nel marker
     * @return Script JS per aggiungere il marker
     */
    public String createCustomMarkerScript(String editorId, int lineNum, String cssClass, String content) {
        return String.format(
            "const marker = document.createElement('div'); " +
            "marker.className = 'ace_custom-marker %2$s'; " +
            "marker.textContent = '%3$s'; " +
            "const y = (%1$d - %4$s.renderer.getFirstVisibleRow()) * %4$s.renderer.lineHeight; " +
            "marker.style.top = y + 'px'; " +
            "marker.style.left = '5px'; " +
            "%4$s.container.appendChild(marker);",
            lineNum - 1, cssClass, content, editorId
        );
    }

    /**
     * Crea uno script per ottenere il contenuto corrente dell'editor.
     * 
     * @param editorId L'ID dell'editor
     * @return Script JS per ottenere il valore
     * 
     * @see Ace Editor API: Editor.getValue()
     */
    public String createGetContentScript(String editorId) {
        return String.format("%s.getValue();", editorId);
    }

    /**
     * Crea uno script per focalizzare l'editor e posizionare il cursore.
     * 
     * @param editorId L'ID dell'editor
     * @param lineNum Il numero di riga (1-based)
     * @param columnNum Il numero di colonna (0-based)
     * @return Script JS per il posizionamento del cursore
     * 
     * @see Ace Editor API: Editor.focus(), Editor.gotoLine()
     */
    public String createFocusScript(String editorId, int lineNum, int columnNum) {
        return String.format(
            "%1$s.focus(); %1$s.gotoLine(%2$d, %3$d, true);",
            editorId, lineNum, columnNum
        );
    }

   

/**
 * Crea uno script JavaScript per rimuovere tutti i marker di errore dall'editor
 * 
 * @return Lo script JavaScript pronto per l'esecuzione
 */
public String clearErrorMarkers() {
    StringBuilder script = new StringBuilder();
    
    // 1. Rimuovi tutte le annotazioni
    script.append("if (editor && editor.session) editor.session.clearAnnotations();");
    
    // 2. Rimuovi tutti i marker di riga
    script.append("if (window.aceMarkers) {");
    script.append("  window.aceMarkers.forEach(function(markerId) {");
    script.append("    if (editor && editor.session) editor.session.removeMarker(markerId);");
    script.append("  });");
    script.append("  window.aceMarkers = [];");
    script.append("}");
    
    return script.toString();
}

public String createErrorMarker(int lineNumber, String editorId) {
    return String.format(
        "%s.session.clearAnnotations(); " +
        "if (window.aceMarker) %s.session.removeMarker(window.aceMarker); " +
        "var Range = ace.require('ace/range').Range; " +
        "window.aceMarker = %s.session.addMarker( " +
        "   new Range(%d, 0, %d, Infinity), " +
        "   'ace_error-line', " +
        "   'fullLine' " +
        "); " +
        "%s.session.setAnnotations([{ " +
        "   row: %d, column: 0, text: 'Errore qui', type: 'error' " +
        "}]); " +
        "%s.gotoLine(%d, 0, true); " +
        "%s.scrollToLine(%d, true, true);",
        editorId, editorId, editorId, 
        lineNumber-1, lineNumber-1,
        editorId,
        lineNumber-1,
        editorId, lineNumber,
        editorId, lineNumber-1
    );
}


public String createErrorMarker(String highlight) {
    StringBuilder script = new StringBuilder();
    
    // 1. Prima puliamo eventuali marker esistenti
    script.append(clearErrorMarkers());
    
    // 2. Troviamo la riga corrispondente all'elemento da evidenziare
    script.append("var targetElement = document.querySelector('").append(highlight).append("');");
    script.append("if (targetElement) {");
    script.append("  var lineNumber = parseInt(targetElement.getAttribute('data-line')) || 1;");
    
    // 3. Aggiungiamo l'annotazione (segnalibro rosso a sinistra)
    script.append("  editor.session.setAnnotations([{");
    script.append("    row: lineNumber - 1,");
    script.append("    column: 0,");
    script.append("    text: 'Errore qui',");
    script.append("    type: 'error'");
    script.append("  }]);");
    
    // 4. Aggiungiamo un marker rosso nella gutter (area numeri di riga)
    script.append("  var Range = ace.require('ace/range').Range;");
    script.append("  var markerId = editor.session.addMarker(");
    script.append("    new Range(lineNumber - 1, 0, lineNumber - 1, Infinity),");
    script.append("    'ace_error-line',");
    script.append("    'fullLine'");
    script.append("  );");
    
    // 5. Memorizziamo l'ID del marker per poterlo rimuovere dopo
    script.append("  if (!window.aceMarkers) window.aceMarkers = [];");
    script.append("  window.aceMarkers.push(markerId);");
    
    // 6. Spostiamo il cursore e scrolliamo alla riga interessata
    script.append("  editor.gotoLine(lineNumber, 0, true);");
    script.append("  editor.scrollToLine(lineNumber - 1, true, true);");
    script.append("}");
    
    return script.toString();
}


public String createErrorMarkerScript(String editorVar, Object lineNumber, String message) {
    return String.format(
        "%s.getSession().setAnnotations([{" +
            "row: %s - 1," +
            "column: 0," +
            "text: \"%s\"," +
            "type: \"error\"" +
        "}]);",
        editorVar, lineNumber.toString(), escapeJs(message)
    );
}

public String createHighlightLineScript(String editorVar, Object lineNumber, String cssClass) {
    return String.format(
        "var Range = ace.require('ace/range').Range;" +
        "%s.getSession().addMarker(" +
            "new Range(%s - 1, 0, %s - 1, 1)," +
            "\"%s\", \"fullLine\");",
        editorVar, lineNumber.toString(), lineNumber.toString(), cssClass
    );
}

private String escapeJs(String input) {
    if (input == null) return "";
    return input.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
}

/**
 * Crea uno script per evidenziare un errore nell'editor e scorrere alla riga corrispondente.
 * 
 * @param lineNumber Il numero di riga dell'errore
 * @return Script JS completo per evidenziare l'errore
 */
public String createHighlightErrorScript(int lineNumber) {
    return String.format(
        "document.addEventListener('DOMContentLoaded', function() {" +
        "   const editor = ace.edit('editor');" +
        "   %s" +
        "   %s" +
        "   editor.scrollToLine(%d, true, true);" +
        "});",
        createErrorMarkerScript("editor", lineNumber, "Errore di compilazione"),
        createHighlightLineScript("editor", lineNumber, "error-line"),
        lineNumber
    );
}

/**
 * Crea uno script per la funzione highlightErrorInEditor che evidenzia gli errori nell'editor.
 * 
 * @return Script JS per la funzione highlightErrorInEditor
 */
public String createHighlightErrorInEditorFunction() {
    return "function highlightErrorInEditor(lineNumber) {\n" +
           "    const editor = ace.edit('editor');\n" +
           "    " + createErrorMarkerScript("editor", "lineNumber", "Errore di test") + "\n" +
           "    " + createHighlightLineScript("editor", "lineNumber", "error-line") + "\n" +
           "    editor.scrollToLine(lineNumber, true, true);\n" +
           "    editor.gotoLine(lineNumber, 0, 0, true);\n" +
           "}";
}

/**
 * Crea uno script per la funzione findTestLine che trova la riga di un test nell'editor.
 * 
 * @return Script JS per la funzione findTestLine
 */
public String createFindTestLineFunction() {
    return "function findTestLine(testId) {\n" +
           "    const testContent = ace.edit('testEditor').getValue();\n" +
           "    const testRegex = new RegExp('\\\\b' + testId + '\\\\b');\n" +
           "    const lines = testContent.split('\\n');\n" +
           "    for (let i = 0; i < lines.length; i++) {\n" +
           "        if (testRegex.test(lines[i])) {\n" +
           "            return i + 1;\n" +
           "        }\n" +
           "    }\n" +
           "    return 1;\n" +
           "}";
}

/**
 * Crea uno script per gestire i click sui link dei test falliti.
 * 
 * @return Script JS per la gestione dei click
 */
public String createTestClickHandlerScript() {
    return "document.addEventListener('click', function(e) {\n" +
           "    if (e.target.matches('a[data-testid]')) {\n" +
           "        const testId = e.target.getAttribute('data-testid');\n" +
           "        const lineNumber = findTestLine(testId.split('_')[1]);\n" +
           "        const testEditor = ace.edit('testEditor');\n" +
           "        " + createErrorMarkerScript("testEditor", "lineNumber", "Test fallito") + "\n" +
           "        " + createHighlightLineScript("testEditor", "lineNumber", "error-line") + "\n" +
           "        testEditor.scrollToLine(lineNumber, true, true);\n" +
           "        testEditor.gotoLine(lineNumber, 0, 0, true);\n" +
           "    }\n" +
           "});";
}

/**
 * Crea uno script completo per la gestione degli errori nei test.
 * Include le funzioni highlightErrorInEditor, findTestLine e il gestore dei click.
 * 
 * @return Script JS completo per la gestione degli errori nei test
 */
public String createTestErrorHandlingScript() {
    return "<script>\n" +
           createHighlightErrorInEditorFunction() + "\n\n" +
           createFindTestLineFunction() + "\n\n" +
           createTestClickHandlerScript() + "\n" +
           "</script>";
}

/**
 * Crea uno script per evidenziare una riga specifica nell'editor dei test.
 * 
 * @param highlightLine Il numero di riga da evidenziare
 * @return Script JS per evidenziare la riga nell'editor dei test
 */
public String createTestHighlightScript(int highlightLine) {
    return "<script>" +
           "document.addEventListener('DOMContentLoaded', function() {" +
           "  highlightError(" + highlightLine + ", 'testEditor');" +
           "  ace.edit('testEditor').scrollToLine(" + highlightLine + ", true, true);" +
           "});" +
           "</script>";
}

}
