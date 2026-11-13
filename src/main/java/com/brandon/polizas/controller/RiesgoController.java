package com.brandon.polizas.controller;

import com.brandon.polizas.dto.RiesgoResponse;
import com.brandon.polizas.service.RiesgoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de riesgos individuales. */
@Tag(name = "Riesgos")
@RestController
@RequestMapping("/riesgos")
public class RiesgoController {

    private final RiesgoService riesgoService;

    public RiesgoController(RiesgoService riesgoService) {
        this.riesgoService = riesgoService;
    }

    @Operation(summary = "Cancela un riesgo")
    @PostMapping("/{id}/cancelar")
    public RiesgoResponse cancelar(@PathVariable Long id) {
        return riesgoService.cancelar(id);
    }
}
