package it.sistemaquiz.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sistemaquiz.entity.Utente;
import it.sistemaquiz.repository.UtenteRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/utenti")
@RestController
public class UtenteController {

	@Autowired UtenteRepository utenteRepository;
	
	@GetMapping
    public ResponseEntity<List<Utente>> getAllUtenti() {
        List<Utente> utenti = new ArrayList<>();

        utenteRepository.findAll().forEach(utenti::add);
        return ResponseEntity.ok(utenti);
    }
	
	@GetMapping("/{id}")
	public ResponseEntity<?> getDettagliUtenti(@PathVariable Long id) {
	    Utente utente = utenteRepository.findById(id)
	            .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));

	    Map<String, String> datiUtente = new HashMap<>();
	    datiUtente.put("nome", utente.getNome());
	    datiUtente.put("cogome", utente.getCognome());
	    datiUtente.put("matricola", utente.getMatricola());

	    return ResponseEntity.ok(utente);
	}
	
}