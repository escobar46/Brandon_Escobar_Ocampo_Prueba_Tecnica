package com.brandon.polizas.controller;

import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.repository.PolizaRepository;
import com.brandon.polizas.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion: levantan la aplicacion completa (filtro, controller, service, JPA, H2)
 * y llaman a los endpoints como lo haria un cliente. Solo el CORE y las notificaciones estan simulados.
 * Cada prueba corre dentro de una transaccion que se revierte al terminar, asi no se afectan entre si.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolizaApiIntegrationTest {

    private static final String HEADER = "x-api-key";
    private static final String KEY = "123456";
    private static final String BODY_RIESGO = "{\"arrendatario\":\"Juan Perez\",\"direccionInmueble\":\"Calle 1 # 2-3\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PolizaRepository polizaRepository;

    @MockitoBean
    CorePort corePort;

    @MockitoBean
    NotificacionService notificacionService;

    @BeforeEach
    void limpiarDatosDeEjemplo() {
        polizaRepository.deleteAll(); // cascade ALL elimina tambien los riesgos
        polizaRepository.flush();
    }

    // ---------- seguridad (RN-10) ----------

    @Test
    void CA16_sin_api_key_responde_401() throws Exception {
        mockMvc.perform(get("/polizas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void CA16_con_api_key_incorrecta_responde_401() throws Exception {
        mockMvc.perform(get("/polizas").header(HEADER, "otra-clave"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CA16_los_endpoints_post_tambien_exigen_api_key() throws Exception {
        mockMvc.perform(post("/polizas/1/renovar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/core-mock/evento").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":1}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(corePort);
    }

    @Test
    void CA16_con_api_key_correcta_responde_200() throws Exception {
        mockMvc.perform(get("/polizas").header(HEADER, KEY))
                .andExpect(status().isOk());
    }

    // ---------- listar y filtrar ----------

    @Test
    void CA14_filtra_por_tipo_y_estado() throws Exception {
        crearPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.ACTIVA, "1500000.00", 12, 1);
        crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 2);
        crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA, "900000.00", 6, 1);
        crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.RENOVADA, "800000.00", 12, 1);

        mockMvc.perform(get("/polizas").header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        mockMvc.perform(get("/polizas").header(HEADER, KEY).param("tipo", "COLECTIVA"))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/polizas").header(HEADER, KEY).param("estado", "CANCELADA"))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/polizas").header(HEADER, KEY).param("tipo", "COLECTIVA").param("estado", "ACTIVA"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("COLECTIVA"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
    }

    @Test
    void CA14_combinacion_sin_resultados_devuelve_lista_vacia() throws Exception {
        crearPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.ACTIVA, "1500000.00", 12, 1);

        mockMvc.perform(get("/polizas").header(HEADER, KEY).param("tipo", "INDIVIDUAL").param("estado", "CANCELADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void CA15_filtro_con_valor_invalido_responde_400() throws Exception {
        mockMvc.perform(get("/polizas").header(HEADER, KEY).param("tipo", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- renovar ----------

    @Test
    void CA01_renovar_actualiza_canon_prima_estado_y_notifica_al_core() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.ACTIVA, "1500000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/renovar", p.getId()).header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RENOVADA"))
                .andExpect(jsonPath("$.canonMensual").value(1575000.0))
                .andExpect(jsonPath("$.prima").value(18900000.0))
                .andExpect(jsonPath("$.fechaInicio").value("2027-01-01"));

        verify(corePort, times(1)).notificarEvento("ACTUALIZACION", p.getId());
    }

    @Test
    void CA03_renovar_poliza_cancelada_responde_409_y_no_notifica() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.CANCELADA, "1500000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/renovar", p.getId()).header(HEADER, KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value(containsString("cancelada")));

        verifyNoInteractions(corePort);
    }

    @Test
    void CA04_renovar_dos_veces_aplica_el_ipc_dos_veces() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1500000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/renovar", p.getId()).header(HEADER, KEY)).andExpect(status().isOk());
        mockMvc.perform(post("/polizas/{id}/renovar", p.getId()).header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonMensual").value(1653750.0))
                .andExpect(jsonPath("$.fechaInicio").value("2028-01-01"));

        verify(corePort, times(2)).notificarEvento("ACTUALIZACION", p.getId());
    }

    // ---------- cancelar poliza ----------

    @Test
    void CA05_cancelar_poliza_cancela_sus_riesgos_y_notifica_una_vez() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 3);

        mockMvc.perform(post("/polizas/{id}/cancelar", p.getId()).header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        mockMvc.perform(get("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.estado != 'CANCELADO')]").isEmpty());

        verify(corePort, times(1)).notificarEvento("ACTUALIZACION", p.getId());
    }

    @Test
    void CA06_cancelar_poliza_dos_veces_responde_409_la_segunda() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/cancelar", p.getId()).header(HEADER, KEY)).andExpect(status().isOk());
        mockMvc.perform(post("/polizas/{id}/cancelar", p.getId()).header(HEADER, KEY)).andExpect(status().isConflict());

        verify(corePort, times(1)).notificarEvento("ACTUALIZACION", p.getId());
    }

    // ---------- agregar riesgos ----------

    @Test
    void CA07_agregar_riesgo_a_individual_responde_409_y_sigue_con_un_riesgo() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.ACTIVA, "1500000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RIESGO))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY))
                .andExpect(jsonPath("$.length()").value(1));
        verifyNoInteractions(corePort);
    }

    @Test
    void CA08_agregar_riesgo_a_colectiva_responde_201_y_aparece_en_el_listado() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 2);

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RIESGO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrendatario").value("Juan Perez"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.polizaId").value(p.getId()));

        mockMvc.perform(get("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY))
                .andExpect(jsonPath("$.length()").value(3));

        verify(corePort).notificarEvento("ACTUALIZACION", p.getId());
    }

    @Test
    void CA09_agregar_riesgo_a_colectiva_cancelada_responde_409() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA, "1200000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RIESGO))
                .andExpect(status().isConflict());
    }

    @Test
    void CA10_agregar_riesgo_sin_arrendatario_responde_400_con_el_campo_en_el_mensaje() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"direccionInmueble\":\"Calle 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("arrendatario")));
    }

    @Test
    void CA10_agregar_riesgo_con_textos_vacios_o_json_malformado_responde_400() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 1);

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"arrendatario\":\"  \",\"direccionInmueble\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/polizas/{id}/riesgos", p.getId()).header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(corePort);
    }

    // ---------- cancelar riesgo ----------

    @Test
    void CA11_cancelar_riesgo_lo_deja_cancelado_y_notifica_con_el_id_de_la_poliza() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 2);
        Long riesgoId = p.getRiesgos().get(0).getId();

        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.polizaId").value(p.getId()));

        verify(corePort).notificarEvento("ACTUALIZACION", p.getId());
    }

    @Test
    void CA12_cancelar_riesgo_dos_veces_responde_409_la_segunda() throws Exception {
        Poliza p = crearPoliza(TipoPoliza.COLECTIVA, EstadoPoliza.ACTIVA, "1200000.00", 12, 1);
        Long riesgoId = p.getRiesgos().get(0).getId();

        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, KEY)).andExpect(status().isOk());
        mockMvc.perform(post("/riesgos/{id}/cancelar", riesgoId).header(HEADER, KEY)).andExpect(status().isConflict());

        verify(corePort, times(1)).notificarEvento("ACTUALIZACION", p.getId());
    }

    // ---------- 404 ----------

    @Test
    void CA13_ids_inexistentes_responden_404() throws Exception {
        mockMvc.perform(get("/polizas/9999/riesgos").header(HEADER, KEY)).andExpect(status().isNotFound());
        mockMvc.perform(post("/polizas/9999/renovar").header(HEADER, KEY)).andExpect(status().isNotFound());
        mockMvc.perform(post("/polizas/9999/cancelar").header(HEADER, KEY)).andExpect(status().isNotFound());
        mockMvc.perform(post("/polizas/9999/riesgos").header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RIESGO))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/riesgos/9999/cancelar").header(HEADER, KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(containsString("9999")));

        verifyNoInteractions(corePort);
    }

    // ---------- core-mock ----------

    @Test
    void CA18_core_mock_acepta_evento_valido_con_202() throws Exception {
        mockMvc.perform(post("/core-mock/evento").header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"));
    }

    @Test
    void CA18_core_mock_rechaza_evento_sin_poliza_id_con_400() throws Exception {
        mockMvc.perform(post("/core-mock/evento").header(HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"ACTUALIZACION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("polizaId")));
    }

    // ---------- documentacion OpenAPI (RN-10) ----------

    @Test
    void CA21_la_documentacion_openapi_responde_sin_api_key_y_declara_el_header() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("x-api-key"))
                .andExpect(jsonPath("$.paths['/polizas/{id}/renovar']").exists());
    }

    @Test
    void CA21_swagger_ui_responde_sin_api_key() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    // ---------- utilidades ----------

    private Poliza crearPoliza(TipoPoliza tipo, EstadoPoliza estado, String canon, int meses, int numRiesgos) {
        Poliza p = new Poliza();
        p.setTipo(tipo);
        p.setEstado(estado);
        p.setFechaInicio(LocalDate.of(2026, 1, 1));
        p.setVigenciaMeses(meses);
        BigDecimal canonMensual = new BigDecimal(canon);
        p.setCanonMensual(canonMensual);
        p.setPrima(canonMensual.multiply(BigDecimal.valueOf(meses)));

        for (int i = 1; i <= numRiesgos; i++) {
            Riesgo r = new Riesgo();
            r.setPoliza(p);
            r.setArrendatario("Arrendatario " + i);
            r.setDireccionInmueble("Direccion " + i);
            r.setEstado(estado == EstadoPoliza.CANCELADA ? EstadoRiesgo.CANCELADO : EstadoRiesgo.ACTIVO);
            p.getRiesgos().add(r);
        }
        return polizaRepository.save(p);
    }
}
