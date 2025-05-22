// File: SistemaQuiz/src/main/java/it/sistemaquiz/model/EseguiTestRequest.java
package it.sistemaquiz.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dati necessari per eseguire un test su una domanda specifica.")
public class EseguiTestRequest {

    @NotNull(message = "ERRORE: Campo 'ID Domanda' mancante. È necessario specificare l'ID della domanda da testare.")
    @Min(value = 1, message = "ERRORE: Campo 'ID Domanda' non valido. Deve essere un numero positivo maggiore di zero.")
    @Schema(description = "L'ID univoco della domanda per cui eseguire il test.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long idDomanda;

    @NotBlank(message = "ERRORE: Campo 'Codice' mancante o vuoto. È necessario inserire il codice Java da testare.")
    @Size(min = 10, max = 10000, message = "ERRORE: Campo 'Codice' non valido. Il codice deve avere tra 10 e 10000 caratteri (attualmente: ${validatedValue == null ? 0 : validatedValue.length()} caratteri).")
    @Schema(description = "Il codice Java sorgente fornito dall'utente da compilare e testare.",
            example = "public class Soluzione {\n    public String saluta() {\n        return \"Ciao Mondo!\";\n    }\n}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String codice;

    // Getters
    public Long getIdDomanda() {
        return idDomanda;
    }

    // Setters
    public void setIdDomanda(Long idDomanda) {
        this.idDomanda = idDomanda;
    }

    public String getCodice() {
        return codice;
    }

    public void setCodice(String codice) {
        this.codice = codice;
    }
}