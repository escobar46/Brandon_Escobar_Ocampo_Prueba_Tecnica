package com.brandon.polizas.controller;

import com.brandon.polizas.exception.ApiError;
import com.brandon.polizas.exception.GlobalExceptionHandler;
import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.repository.PolizaRepository;
import com.brandon.polizas.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloqueo optimista (RN-12). La clase no es @Transactional a proposito: cada guardado
 * hace commit real, que es cuando se compara la version del registro.
 * Usa la misma configuracion que PolizaApiIntegrationTest para compartir el contexto de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrenciaIntegrationTest {

    @Autowired
    PolizaRepository polizaRepository;

    @MockitoBean
    CorePort corePort;

    @MockitoBean
    NotificacionService notificacionService;

    @Test
    void CA19_si_dos_usuarios_modifican_la_misma_poliza_el_segundo_es_rechazado() {
        Long id = polizaRepository.save(polizaActiva()).getId();

        // Los dos usuarios leen la poliza cuando esta en la misma version
        Poliza delUsuarioA = polizaRepository.findById(id).orElseThrow();
        Poliza delUsuarioB = polizaRepository.findById(id).orElseThrow();

        delUsuarioA.setEstado(EstadoPoliza.RENOVADA);
        polizaRepository.save(delUsuarioA);

        delUsuarioB.setEstado(EstadoPoliza.CANCELADA);
        assertThatThrownBy(() -> polizaRepository.save(delUsuarioB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(polizaRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
    }

    @Test
    void CA19_el_conflicto_se_responde_con_409() {
        ApiError error = new GlobalExceptionHandler()
                .modificacionSimultanea(new ObjectOptimisticLockingFailureException(Poliza.class, 1L));

        assertThat(error.status()).isEqualTo(409);
    }

    private Poliza polizaActiva() {
        Poliza p = new Poliza();
        p.setTipo(TipoPoliza.INDIVIDUAL);
        p.setEstado(EstadoPoliza.ACTIVA);
        p.setFechaInicio(LocalDate.of(2026, 1, 1));
        p.setVigenciaMeses(12);
        p.setCanonMensual(new BigDecimal("1000000.00"));
        p.setPrima(new BigDecimal("12000000.00"));
        return p;
    }
}
