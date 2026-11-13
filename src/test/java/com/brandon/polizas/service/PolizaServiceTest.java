package com.brandon.polizas.service;

import com.brandon.polizas.TestData;
import com.brandon.polizas.dto.PolizaResponse;
import com.brandon.polizas.exception.RecursoNoEncontradoException;
import com.brandon.polizas.exception.ReglaNegocioException;
import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.repository.PolizaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.brandon.polizas.model.EstadoPoliza.*;
import static com.brandon.polizas.model.EstadoRiesgo.*;
import static com.brandon.polizas.model.TipoPoliza.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de PolizaService, con el repositorio, el CORE y las notificaciones simulados (mocks). */
@ExtendWith(MockitoExtension.class)
class PolizaServiceTest {

    @Mock
    PolizaRepository polizaRepository;

    @Mock
    CorePort corePort;

    @Mock
    NotificacionService notificacionService;

    PolizaService service;

    @BeforeEach
    void setUp() {
        service = new PolizaService(polizaRepository, corePort, notificacionService, new BigDecimal("0.0500"));
    }

    // ---------- renovar ----------

    @Test
    void CA01_renovar_aplica_ipc_a_canon_y_prima_y_marca_renovada() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        PolizaResponse r = service.renovar(1L);

        assertThat(r.canonMensual()).isEqualByComparingTo("1575000.00");
        assertThat(r.prima()).isEqualByComparingTo("18900000.00");
        assertThat(r.estado()).isEqualTo(RENOVADA);
        assertThat(r.fechaInicio()).isEqualTo(LocalDate.of(2027, 1, 1)); // RN-11
        verify(polizaRepository).saveAndFlush(p);
        verify(corePort).notificarEvento("ACTUALIZACION", 1L);
    }

    @Test
    void CA02_renovar_redondea_half_up_a_dos_decimales() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1000000.33", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        PolizaResponse r = service.renovar(1L);

        // 1000000.33 * 1.05 = 1050000.3465 -> 1050000.35
        assertThat(r.canonMensual()).isEqualByComparingTo("1050000.35");
        assertThat(r.prima()).isEqualByComparingTo("12600004.20");
    }

    @Test
    void CA03_renovar_poliza_cancelada_falla_y_no_notifica_al_core() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, CANCELADA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.renovar(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("cancelada");

        verify(polizaRepository, never()).saveAndFlush(any(Poliza.class));
        verifyNoInteractions(corePort);
        assertThat(p.getCanonMensual()).isEqualByComparingTo("1500000.00"); // no se modifico nada
    }

    @Test
    void CA04_una_poliza_renovada_puede_renovarse_de_nuevo() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, RENOVADA, "1575000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        PolizaResponse r = service.renovar(1L);

        assertThat(r.canonMensual()).isEqualByComparingTo("1653750.00");
        assertThat(r.prima()).isEqualByComparingTo("19845000.00");
        assertThat(r.estado()).isEqualTo(RENOVADA);
    }

    @Test
    void CA13_renovar_poliza_inexistente_lanza_no_encontrado() {
        when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(corePort);
    }

    // ---------- cancelar ----------

    @Test
    void CA05_cancelar_poliza_cancela_todos_sus_riesgos() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        TestData.riesgo(10L, p, ACTIVO);
        TestData.riesgo(11L, p, ACTIVO);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));

        PolizaResponse r = service.cancelar(2L);

        assertThat(r.estado()).isEqualTo(CANCELADA);
        assertThat(p.getRiesgos()).hasSize(2).allMatch(rg -> rg.getEstado() == CANCELADO);
        verify(corePort, times(1)).notificarEvento("ACTUALIZACION", 2L);
    }

    @Test
    void CA06_cancelar_poliza_ya_cancelada_falla() {
        Poliza p = TestData.poliza(2L, COLECTIVA, CANCELADA, "1200000.00", 12);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.cancelar(2L))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(corePort);
    }

    @Test
    void CA13_cancelar_poliza_inexistente_lanza_no_encontrado() {
        when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- notificaciones al tomador (RN-13) ----------

    @Test
    void CA20_renovar_notifica_al_tomador_con_el_nuevo_canon() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        service.renovar(1L);

        verify(notificacionService).notificarTomador(eq(1L), contains("1575000.00"));
    }

    @Test
    void CA20_cancelar_notifica_al_tomador() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));

        service.cancelar(2L);

        verify(notificacionService).notificarTomador(eq(2L), contains("cancelada"));
    }

    @Test
    void CA20_si_la_regla_de_negocio_falla_no_se_notifica_al_tomador() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, CANCELADA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.renovar(1L)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> service.cancelar(1L)).isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(notificacionService);
    }

    // ---------- concurrencia (RN-12) ----------

    @Test
    void CA19_si_otra_operacion_modifico_la_poliza_no_se_avisa_a_nadie() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));
        when(polizaRepository.saveAndFlush(p)).thenThrow(new ObjectOptimisticLockingFailureException(Poliza.class, 1L));

        assertThatThrownBy(() -> service.renovar(1L)).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verifyNoInteractions(corePort, notificacionService);
    }

    // ---------- listar ----------

    @Test
    void CA14_listar_con_tipo_y_estado_usa_el_filtro_combinado() {
        Poliza p = TestData.poliza(1L, COLECTIVA, ACTIVA, "1000000.00", 12);
        when(polizaRepository.findByTipoAndEstado(COLECTIVA, ACTIVA)).thenReturn(List.of(p));

        List<PolizaResponse> r = service.listar(COLECTIVA, ACTIVA);

        assertThat(r).hasSize(1);
        verify(polizaRepository, never()).findAll();
    }

    @Test
    void CA14_listar_solo_por_tipo() {
        when(polizaRepository.findByTipo(INDIVIDUAL)).thenReturn(List.of());

        assertThat(service.listar(INDIVIDUAL, null)).isEmpty();

        verify(polizaRepository).findByTipo(INDIVIDUAL);
    }

    @Test
    void CA14_listar_solo_por_estado() {
        when(polizaRepository.findByEstado(CANCELADA)).thenReturn(List.of());

        assertThat(service.listar(null, CANCELADA)).isEmpty();

        verify(polizaRepository).findByEstado(CANCELADA);
    }

    @Test
    void CA14_listar_sin_filtros_trae_todas() {
        Poliza a = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1000000.00", 12);
        Poliza b = TestData.poliza(2L, COLECTIVA, CANCELADA, "900000.00", 6);
        when(polizaRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(service.listar(null, null)).hasSize(2);
    }
}
