package com.brandon.polizas.repository;

import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.TipoPoliza;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso a polizas. Spring Data genera la consulta SQL a partir del nombre de cada metodo. */
public interface PolizaRepository extends JpaRepository<Poliza, Long> {

    List<Poliza> findByTipoAndEstado(TipoPoliza tipo, EstadoPoliza estado);

    List<Poliza> findByTipo(TipoPoliza tipo);

    List<Poliza> findByEstado(EstadoPoliza estado);
}
