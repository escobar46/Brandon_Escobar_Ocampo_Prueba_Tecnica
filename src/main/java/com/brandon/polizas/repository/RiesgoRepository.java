package com.brandon.polizas.repository;

import com.brandon.polizas.model.Riesgo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso a riesgos. */
public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

    List<Riesgo> findByPolizaId(Long polizaId);
}
