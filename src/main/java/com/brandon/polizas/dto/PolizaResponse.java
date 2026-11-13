package com.brandon.polizas.dto;

import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.TipoPoliza;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos de una poliza que devuelve la API. Separa el contrato HTTP de la entidad de base de datos. */
public record PolizaResponse(
        Long id,
        TipoPoliza tipo,
        EstadoPoliza estado,
        LocalDate fechaInicio,
        Integer vigenciaMeses,
        BigDecimal canonMensual,
        BigDecimal prima) {

    /** Convierte la entidad en la respuesta de la API. */
    public static PolizaResponse from(Poliza p) {
        return new PolizaResponse(p.getId(), p.getTipo(), p.getEstado(), p.getFechaInicio(),
                p.getVigenciaMeses(), p.getCanonMensual(), p.getPrima());
    }
}
