package com.brandon.polizas.dto;

import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Riesgo;

/** Datos de un riesgo que devuelve la API. */
public record RiesgoResponse(
        Long id,
        Long polizaId,
        String arrendatario,
        String direccionInmueble,
        EstadoRiesgo estado) {

    /** Convierte la entidad en la respuesta de la API. */
    public static RiesgoResponse from(Riesgo r) {
        return new RiesgoResponse(r.getId(), r.getPoliza().getId(), r.getArrendatario(),
                r.getDireccionInmueble(), r.getEstado());
    }
}
