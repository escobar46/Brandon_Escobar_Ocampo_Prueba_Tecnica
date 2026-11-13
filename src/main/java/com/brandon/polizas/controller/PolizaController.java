package com.brandon.polizas.controller;

import com.brandon.polizas.dto.PolizaResponse;
import com.brandon.polizas.dto.RiesgoRequest;
import com.brandon.polizas.dto.RiesgoResponse;
import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.service.PolizaService;
import com.brandon.polizas.service.RiesgoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de polizas. Solo traduce HTTP y delega en los services; no contiene reglas de negocio. */
@Tag(name = "Polizas")
@RestController
@RequestMapping("/polizas")
public class PolizaController {

    private final PolizaService polizaService;
    private final RiesgoService riesgoService;

    public PolizaController(PolizaService polizaService, RiesgoService riesgoService) {
        this.polizaService = polizaService;
        this.riesgoService = riesgoService;
    }

    @Operation(summary = "Lista polizas", description = "Filtros opcionales y combinables por tipo y estado")
    @GetMapping
    public List<PolizaResponse> listar(@RequestParam(required = false) TipoPoliza tipo,
                                       @RequestParam(required = false) EstadoPoliza estado) {
        return polizaService.listar(tipo, estado);
    }

    @Operation(summary = "Lista los riesgos de una poliza")
    @GetMapping("/{id}/riesgos")
    public List<RiesgoResponse> listarRiesgos(@PathVariable Long id) {
        return riesgoService.listarPorPoliza(id);
    }

    @Operation(summary = "Renueva una poliza", description = "Aplica el IPC a canon y prima y pasa a RENOVADA")
    @PostMapping("/{id}/renovar")
    public PolizaResponse renovar(@PathVariable Long id) {
        return polizaService.renovar(id);
    }

    @Operation(summary = "Cancela una poliza y todos sus riesgos")
    @PostMapping("/{id}/cancelar")
    public PolizaResponse cancelar(@PathVariable Long id) {
        return polizaService.cancelar(id);
    }

    @Operation(summary = "Agrega un riesgo", description = "Solo polizas colectivas que no esten canceladas")
    @PostMapping("/{id}/riesgos")
    @ResponseStatus(HttpStatus.CREATED)
    public RiesgoResponse agregarRiesgo(@PathVariable Long id, @Valid @RequestBody RiesgoRequest request) {
        return riesgoService.agregar(id, request);
    }
}
