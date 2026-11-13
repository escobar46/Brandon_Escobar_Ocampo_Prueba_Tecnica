package com.brandon.polizas.service;

import com.brandon.polizas.TestData;
import com.brandon.polizas.dto.RiesgoRequest;
import com.brandon.polizas.dto.RiesgoResponse;
import com.brandon.polizas.exception.RecursoNoEncontradoException;
import com.brandon.polizas.exception.ReglaNegocioException;
import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.repository.PolizaRepository;
import com.brandon.polizas.repository.RiesgoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.brandon.polizas.model.EstadoPoliza.*;
import static com.brandon.polizas.model.EstadoRiesgo.*;
import static com.brandon.polizas.model.TipoPoliza.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de RiesgoService, con los repositorios y el CORE simulados (mocks). */
@ExtendWith(MockitoExtension.class)
class RiesgoServiceTest {

    @Mock
    PolizaRepository polizaRepository;

    @Mock
    RiesgoRepository riesgoRepository;

    @Mock
    CorePort corePort;

    RiesgoService service;

    @BeforeEach
    void setUp() {
        service = new RiesgoService(polizaRepository, riesgoRepository, corePort);
    }

    // ---------- agregar ----------

    @Test
    void CA07_agregar_riesgo_a_poliza_individual_falla() {
        Poliza p = TestData.poliza(1L, INDIVIDUAL, ACTIVA, "1500000.00", 12);
        when(polizaRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.agregar(1L, new RiesgoRequest("Juan", "Calle 1")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("colectivas");

        verify(riesgoRepository, never()).saveAndFlush(any(Riesgo.class));
        verifyNoInteractions(corePort);
    }

    @Test
    void CA08_agregar_riesgo_a_colectiva_activa_lo_guarda_activo_y_notifica() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));

        RiesgoResponse r = service.agregar(2L, new RiesgoRequest("Juan", "Calle 1"));

        ArgumentCaptor<Riesgo> captor = ArgumentCaptor.forClass(Riesgo.class);
        verify(riesgoRepository).saveAndFlush(captor.capture());
        Riesgo guardado = captor.getValue();
        assertThat(guardado.getEstado()).isEqualTo(ACTIVO);
        assertThat(guardado.getArrendatario()).isEqualTo("Juan");
        assertThat(guardado.getDireccionInmueble()).isEqualTo("Calle 1");
        assertThat(guardado.getPoliza()).isSameAs(p);
        assertThat(r.polizaId()).isEqualTo(2L);
        verify(corePort).notificarEvento("ACTUALIZACION", 2L);
    }

    @Test
    void CA09_agregar_riesgo_a_colectiva_cancelada_falla() {
        Poliza p = TestData.poliza(2L, COLECTIVA, CANCELADA, "1200000.00", 12);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.agregar(2L, new RiesgoRequest("Juan", "Calle 1")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("cancelada");

        verify(riesgoRepository, never()).saveAndFlush(any(Riesgo.class));
    }

    @Test
    void CA13_agregar_riesgo_a_poliza_inexistente_lanza_no_encontrado() {
        when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agregar(99L, new RiesgoRequest("Juan", "Calle 1")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- cancelar ----------

    @Test
    void CA11_cancelar_riesgo_activo_lo_deja_cancelado_y_notifica_con_el_id_de_la_poliza() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        Riesgo riesgo = TestData.riesgo(10L, p, ACTIVO);
        when(riesgoRepository.findById(10L)).thenReturn(Optional.of(riesgo));

        RiesgoResponse r = service.cancelar(10L);

        assertThat(r.estado()).isEqualTo(CANCELADO);
        assertThat(riesgo.getEstado()).isEqualTo(CANCELADO);
        verify(corePort).notificarEvento("ACTUALIZACION", 2L);
    }

    @Test
    void CA12_cancelar_riesgo_ya_cancelado_falla() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        Riesgo riesgo = TestData.riesgo(10L, p, CANCELADO);
        when(riesgoRepository.findById(10L)).thenReturn(Optional.of(riesgo));

        assertThatThrownBy(() -> service.cancelar(10L))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(corePort);
    }

    @Test
    void CA13_cancelar_riesgo_inexistente_lanza_no_encontrado() {
        when(riesgoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- listar ----------

    @Test
    void listar_riesgos_de_una_poliza_existente() {
        Poliza p = TestData.poliza(2L, COLECTIVA, ACTIVA, "1200000.00", 12);
        Riesgo a = TestData.riesgo(10L, p, ACTIVO);
        Riesgo b = TestData.riesgo(11L, p, CANCELADO);
        when(polizaRepository.findById(2L)).thenReturn(Optional.of(p));
        when(riesgoRepository.findByPolizaId(2L)).thenReturn(List.of(a, b));

        List<RiesgoResponse> r = service.listarPorPoliza(2L);

        assertThat(r).hasSize(2);
        assertThat(r).extracting(RiesgoResponse::estado).containsExactly(ACTIVO, CANCELADO);
    }

    @Test
    void CA13_listar_riesgos_de_poliza_inexistente_lanza_no_encontrado() {
        when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarPorPoliza(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(riesgoRepository);
    }
}
