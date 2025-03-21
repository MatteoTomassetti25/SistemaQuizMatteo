package it.sistemaquiz.entity;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.*;

@Table(name = "utenti")
@Entity
public class Utente implements UserDetails {
	
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Integer id;
    private String nome;
    private String cognome;
    @Column(nullable = false)
    private String matricola;
    @Column(nullable = false)
    private String password;
    @OneToMany(mappedBy = "utente")
    @JsonManagedReference
    private List<Codice> codici;

    public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getCognome() {
		return cognome;
	}

	public void setCognome(String cognome) {
		this.cognome = cognome;
	}

	public String getMatricola() {
		return matricola;
	}

	public void setMatricola(String matricola) {
		this.matricola = matricola;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public List<Codice> getCodici() {
		return codici;
	}

	public void setCodici(List<Codice> codici) {
		this.codici = codici;
	}

	@Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

	@Override
	public String getUsername() {
		return this.matricola;
	}

}