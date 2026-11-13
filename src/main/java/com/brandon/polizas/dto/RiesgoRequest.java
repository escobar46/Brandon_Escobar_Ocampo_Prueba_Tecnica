package com.brandon.polizas.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code POST /polizas/{id}/riesgos}. Ambos campos son obligatorios y no pueden estar vacios. */
public record RiesgoRequest(
        @NotBlank(message = "es obligatorio") String arrendatario,
        @NotBlank(message = "es obligatorio") String direccionInmueble) {
}
