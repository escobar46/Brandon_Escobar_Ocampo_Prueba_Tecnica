package com.brandon.polizas.config;

import com.brandon.polizas.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Seguridad minima: toda peticion debe traer el header {@code x-api-key} con el valor configurado.
 * Si falta o es incorrecto, responde 401 sin llegar a los controllers.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "x-api-key";

    private final String apiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(@Value("${polizas.api-key}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    /** Rutas que no exigen api key: la consola H2 (desarrollo) y la documentacion, que no expone datos. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/h2-console") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (esApiKeyValida(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError error = new ApiError(Instant.now(), HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), "x-api-key ausente o invalida");
        objectMapper.writeValue(response.getWriter(), error);
    }

    /**
     * Compara la clave recibida con la configurada.
     * Se usa {@link MessageDigest#isEqual} porque tarda lo mismo sin importar en que caracter difieran,
     * asi no se puede adivinar la clave midiendo tiempos de respuesta.
     */
    private boolean esApiKeyValida(String recibida) {
        return recibida != null && MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8));
    }
}
