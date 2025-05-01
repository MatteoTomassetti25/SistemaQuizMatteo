package it.sistemaquiz.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Table(name = "domande")
@Entity
public class Domanda {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	@Column(length=1000)
	private String domanda;
	@Column(length=3000)
    private String test; // classe di test
	@OneToMany(mappedBy = "domanda")
    @JsonManagedReference
    private List<Codice> codici;
    
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getDomanda() {
		return domanda;
	}
	
	public void setDomanda(String domanda) {
		this.domanda = domanda;
	}
	
	public String getTest() {
		return test;
	}
	
	public void setTest(String test) {
		this.test = test;
	}

	public List<Codice> getCodici() {
		return codici;
	}

	public void setCodici(List<Codice> codici) {
		this.codici = codici;
	}
    
}