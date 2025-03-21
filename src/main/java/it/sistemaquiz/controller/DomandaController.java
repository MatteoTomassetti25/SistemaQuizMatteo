package it.sistemaquiz.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.repository.DomandaRepository;

@RequestMapping("/domande")
@RestController
public class DomandaController {
	
	@Autowired DomandaRepository domandaRepository;

	@GetMapping
    public ResponseEntity<List<Domanda>> getAllDomande() {
        List<Domanda> domande = domandaRepository.findAll();
        return ResponseEntity.ok(domande);
    }
	
	@GetMapping("/{id}")
	public ResponseEntity<?> getDettagliDomanda(@PathVariable Long id) {
	    Domanda domanda = domandaRepository.findById(id)
	            .orElseThrow(() -> new IllegalArgumentException("Domanda non trovata"));

	    Map<String, String> dettagliDomanda = new HashMap<>();
	    dettagliDomanda.put("domanda", domanda.getDomanda());
	    dettagliDomanda.put("test", domanda.getTest());

	    return ResponseEntity.ok(dettagliDomanda);
	}

}
