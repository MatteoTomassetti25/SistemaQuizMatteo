package it.sistemaquiz.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.sistemaquiz.entity.Codice;

@Repository
public interface CodiceRepository extends JpaRepository<Codice, Long> {
	
	List<Codice> findByUtenteId(Long utenteId);
	
}