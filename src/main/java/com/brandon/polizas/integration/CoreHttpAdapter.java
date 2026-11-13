package com.brandon.polizas.integration;

import com.brandon.polizas.dto.EventoCoreRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Envia los eventos al CORE por HTTP. En este ejercicio apunta al mock {@code /core-mock/evento};
 * en produccion apuntaria al servicio de edicion de la capa media (WebLogic).
 */
@Slf4j
@Component
public class CoreHttpAdapter implements CorePort {

    private final RestClient restClient;

    /**
     * @param baseUrl URL base del CORE
     * @param apiKey  api-key que se envia en el header {@code x-api-key}
     * @param timeout tiempo maximo para conectar y para esperar la respuesta
     */
    public CoreHttpAdapter(@Value("${core.base-url}") String baseUrl,
                           @Value("${polizas.api-key}") String apiKey,
                           @Value("${core.timeout}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", apiKey)
                .build();
    }

    @Override
    public void notificarEvento(String evento, Long polizaId) {
        try {
            restClient.post()
                    .uri("/core-mock/evento")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EventoCoreRequest(evento, polizaId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("No se pudo notificar al CORE (evento={}, polizaId={}): {}",
                    evento, polizaId, ex.getMostSpecificCause().toString());
        }
    }
}
