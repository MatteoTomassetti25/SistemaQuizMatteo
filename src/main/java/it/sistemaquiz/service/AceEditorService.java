package it.sistemaquiz.service;

import java.util.Map;

import org.springframework.stereotype.Service;


@Service
public class AceEditorService {

   

    

    //script marker errore alla riga specifica
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

    //marker personalizzato
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

    

    //evidenzia la riga richiesta
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

    

    //stile css per il marker
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

    

    //scorrimento automatico del puntatore
    public String createFocusScript(String editorId, int lineNum, int columnNum) {
        return String.format(
            "%1$s.focus(); %1$s.gotoLine(%2$d, %3$d, true);",
            editorId, lineNum, columnNum
        );
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


public String createHighlightErrorInEditorFunction() {
    return "function highlightErrorInEditor(lineNumber) {\n" +
           "    const editor = ace.edit('editor');\n" +
           "    " + createErrorMarkerScript("editor", "lineNumber", "Errore di test") + "\n" +
           "    " + createHighlightLineScript("editor", "lineNumber", "error-line") + "\n" +
           "    editor.scrollToLine(lineNumber, true, true);\n" +
           "    editor.gotoLine(lineNumber, 0, 0, true);\n" +
           "}";
}


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

//click link errori
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


public String createTestErrorHandlingScript() {
    return "<script>\n" +
           createHighlightErrorInEditorFunction() + "\n\n" +
           createFindTestLineFunction() + "\n\n" +
           createTestClickHandlerScript() + "\n" +
           "</script>";
}


public String createTestHighlightScript(int highlightLine) {
    return "<script>" +
           "document.addEventListener('DOMContentLoaded', function() {" +
           "  highlightError(" + highlightLine + ", 'testEditor');" +
           "  ace.edit('testEditor').scrollToLine(" + highlightLine + ", true, true);" +
           "});" +
           "</script>";
}

}