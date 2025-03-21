package it.sistemaquiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Table(name = "codici")
@Entity
public class Codice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 10000)
    private String codice;
    private boolean risultato; // true se risultato è giusto, false se sbagliato
    @ManyToOne
    @JsonIgnore
    private Utente utente;
    @ManyToOne
    @JsonIgnore
    private Domanda domanda;

    public Codice(String codice, Utente utente, Domanda domanda, boolean risultato) {
    	this.codice = codice;
    	this.utente = utente;
    	this.domanda = domanda;
    	this.risultato = risultato;
    }
    
    public Codice() {
    
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodice() {
        return codice;
    }

    public void setCodice(String codice) {
        this.codice = codice;
    }

	public boolean isRisultato() {
		return risultato;
	}

	public void setRisultato(boolean risultato) {
		this.risultato = risultato;
	}

	public Utente getUtente() {
		return utente;
	}

	public void setUtente(Utente utente) {
		this.utente = utente;
	}

	public Domanda getDomanda() {
		return domanda;
	}

	public void setDomanda(Domanda domanda) {
		this.domanda = domanda;
	}

}
