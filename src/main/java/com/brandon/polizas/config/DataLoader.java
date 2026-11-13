package com.brandon.polizas.config;

import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.repository.PolizaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carga datos de ejemplo al arrancar, porque la API no crea polizas y la BD H2 empieza vacia.
 * <ul>
 *   <li>Poliza 1: individual, activa, 1 riesgo.</li>
 *   <li>Poliza 2: colectiva, activa, 2 riesgos.</li>
 *   <li>Poliza 3: colectiva, cancelada, 1 riesgo cancelado.</li>
 * </ul>
 */
@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner cargarDatos(PolizaRepository polizaRepository) {
        return args -> {
            Poliza individual = nuevaPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.ACTIVA, "1500000", 12);
            agregarRiesgo(individual, "Ana Torres", "Cra 23 # 45-10, Manizales", EstadoRiesgo.ACTIVO);

            Poliza colectiva = nuevaPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000", 12);
            agregarRiesgo(colectiva, "Carlos Ruiz", "Calle 50 # 20-30 Apto 101", EstadoRiesgo.ACTIVO);
            agregarRiesgo(colectiva, "Laura Gomez", "Calle 50 # 20-30 Apto 202", EstadoRiesgo.ACTIVO);

            Poliza cancelada = nuevaPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA, "900000", 6);
            agregarRiesgo(cancelada, "Pedro Mejia", "Av. Santander # 10-05", EstadoRiesgo.CANCELADO);

            polizaRepository.save(individual);
            polizaRepository.save(colectiva);
            polizaRepository.save(cancelada);
        };
    }

    private Poliza nuevaPoliza(TipoPoliza tipo, EstadoPoliza estado, String canon, int meses) {
        BigDecimal canonMensual = new BigDecimal(canon).setScale(2);

        Poliza poliza = new Poliza();
        poliza.setTipo(tipo);
        poliza.setEstado(estado);
        poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
        poliza.setVigenciaMeses(meses);
        poliza.setCanonMensual(canonMensual);
        poliza.setPrima(canonMensual.multiply(BigDecimal.valueOf(meses)));
        return poliza;
    }

    private void agregarRiesgo(Poliza poliza, String arrendatario, String direccion, EstadoRiesgo estado) {
        Riesgo riesgo = new Riesgo();
        riesgo.setPoliza(poliza);
        riesgo.setArrendatario(arrendatario);
        riesgo.setDireccionInmueble(direccion);
        riesgo.setEstado(estado);
        poliza.getRiesgos().add(riesgo);
    }
}
