package com.brandon.polizas.controller;

import com.brandon.polizas.dto.EventoCoreRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simula el CORE transaccional legado. Su unico proposito es dejar en el log
 * que la operacion se intento enviar al CORE.
 */
@Slf4j
@Tag(name = "CORE (mock)")
@RestController
@RequestMapping("/core-mock")
public class CoreMockController {

    @Operation(summary = "Recibe un evento y lo registra en el log")
    @PostMapping("/evento")
    public ResponseEntity<Map<String, String>> recibir(@Valid @RequestBody EventoCoreRequest request) {
        log.info("[CORE-MOCK] Operacion enviada al CORE: evento={}, polizaId={}", request.evento(), request.polizaId());
        return ResponseEntity.accepted().body(Map.of("estado", "RECIBIDO"));
    }
}
