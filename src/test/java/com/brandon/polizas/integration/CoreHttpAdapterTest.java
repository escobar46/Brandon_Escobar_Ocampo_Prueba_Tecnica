package com.brandon.polizas.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Comprueba que un CORE caido o colgado no rompe la operacion (RN-09). */
class CoreHttpAdapterTest {

    @Test
    void CA17_si_el_core_no_responde_no_se_lanza_excepcion() {
        // Puerto 1: nadie escucha, la conexion es rechazada de inmediato
        CoreHttpAdapter adapter = new CoreHttpAdapter("http://localhost:1", "123456", Duration.ofSeconds(2));

        assertThatCode(() -> adapter.notificarEvento("ACTUALIZACION", 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @Timeout(5) // sin timeout en el adapter esta prueba se quedaria esperando para siempre
    void CA17_si_el_core_se_cuelga_se_corta_por_timeout_sin_lanzar_excepcion() throws Exception {
        // El sistema operativo acepta la conexion, pero nadie lee ni responde: simula un CORE colgado
        try (ServerSocket coreColgado = new ServerSocket(0)) {
            CoreHttpAdapter adapter = new CoreHttpAdapter(
                    "http://localhost:" + coreColgado.getLocalPort(), "123456", Duration.ofMillis(300));

            assertThatCode(() -> adapter.notificarEvento("ACTUALIZACION", 1L))
                    .doesNotThrowAnyException();
        }
    }
}
