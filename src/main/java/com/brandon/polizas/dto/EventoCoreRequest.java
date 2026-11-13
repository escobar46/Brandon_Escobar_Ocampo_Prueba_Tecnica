package com.brandon.polizas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del evento que se envia al CORE: {@code {"evento": "ACTUALIZACION", "polizaId": 555}}. */
public record EventoCoreRequest(
        @NotBlank(message = "es obligatorio") String evento,
        @NotNull(message = "es obligatorio") Long polizaId) {
}
