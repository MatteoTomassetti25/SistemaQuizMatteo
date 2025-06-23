package it.sistemaquiz.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.sistemaquiz.entity.Codice;
import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.Utente;

@Repository
public interface CodiceRepository extends JpaRepository<Codice, Long> {

	List<Codice> findByUtenteId(Long utenteId);

	Optional<Codice> findFirstByUtenteAndDomandaOrderByIdDesc(Utente utente, Domanda domanda);
}