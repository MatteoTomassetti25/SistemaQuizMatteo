package it.sistemaquiz.repository;

import it.sistemaquiz.entity.Domanda;
import it.sistemaquiz.entity.compilazioniTest; // Modificato
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface compilazioneTestRepository extends JpaRepository<compilazioniTest, Long> { // Modificato

    // Modificato per cercare usando testSourceHash e domanda
    Optional<compilazioniTest> findByHashTestAndDomanda(
            String hashTest, Domanda domanda
    );
}