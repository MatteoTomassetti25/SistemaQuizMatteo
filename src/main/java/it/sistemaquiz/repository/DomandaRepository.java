package it.sistemaquiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.sistemaquiz.entity.Domanda;

@Repository
public interface DomandaRepository extends JpaRepository<Domanda, Long> {

}