package it.sistemaquiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.sistemaquiz.entity.Utente;

import java.util.Optional;

public interface UtenteRepository extends JpaRepository<Utente, Long> {

	Optional<Utente> findByMatricola(String matricola);
}