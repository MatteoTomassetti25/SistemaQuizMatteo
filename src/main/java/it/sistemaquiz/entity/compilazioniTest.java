package it.sistemaquiz.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tabella_compilazione_test", // Tabella specifica per la cache dei test
        uniqueConstraints = @UniqueConstraint(columnNames = { "hash_codiceTest", "domanda_id" }))
public class compilazioniTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash_codiceTest", nullable = false, length = 64) // Hash del SORGENTE del test MODIFICATO
    private String hashTest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domanda_id", nullable = false)
    private Domanda domanda;

    @Lob // Large Object per i bytecode della classe di test
    @Column(name = "bytecode_codiceTest")
    private byte[] bytecodeClasseTest;

    @Column(name = "nome_classeTest", length = 255) // Nome della classe di test compilata
    private String nomeClasseTest;

    @Lob // Per l'output HTML degli errori di compilazione del test
    @Column(name = "outputCompilazione")
    private String outputCompilazione;

    @Column(name = "successoCompilazioneTest", nullable = false) // Successo della compilazione del SOLO test
    private boolean successoCompilazione;

    @Column(name = "ultimo_utilizzo", nullable = false)
    private LocalDateTime ultimoUtilizzo;

    @Column(name = "creazione", nullable = false, updatable = false)
    private LocalDateTime creazione;

    public compilazioniTest() {
        this.creazione = LocalDateTime.now();
        this.ultimoUtilizzo = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHashTest() {
        return hashTest;
    }

    public void setHashTest(String hashTest) {
        this.hashTest = hashTest;
    }

    public Domanda getDomanda() {
        return domanda;
    }

    public void setDomanda(Domanda domanda) {
        this.domanda = domanda;
    }

    public byte[] getBytecode() {
        return bytecodeClasseTest;
    }

    public void setBytecode(byte[] bytecodeClasseTest) {
        this.bytecodeClasseTest = bytecodeClasseTest;
    }

    public String getNomeClasseTest() {
        return nomeClasseTest;
    }

    public void setNomeClasseTest(String nomeClasseTest) {
        this.nomeClasseTest = nomeClasseTest;
    }

    public String getOutCompilazione() {
        return outputCompilazione;
    }

    public void setOutCompilazione(String outputCompilazione) {
        this.outputCompilazione = outputCompilazione;
    }

    public boolean isSuccessoCompilazione() {
        return successoCompilazione;
    }

    public void setSuccessoCompilazione(boolean successoCompilazione) {
        this.successoCompilazione = successoCompilazione;
    }

    public LocalDateTime getUltimoUtilizzo() {
        return ultimoUtilizzo;
    }

    public void setUltimoUtilizzo(LocalDateTime ultimoUtilizzo) {
        this.ultimoUtilizzo = ultimoUtilizzo;
    }

    public LocalDateTime getCreazione() {
        return creazione;
    }

    public void setCreazione(LocalDateTime creazione) {
        this.creazione = creazione;
    }
}