package com.brandon.polizas;

import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.model.TipoPoliza;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Crea objetos de prueba en memoria (sin base de datos) para las pruebas unitarias. */
public final class TestData {

    private TestData() {
    }

    public static Poliza poliza(Long id, TipoPoliza tipo, EstadoPoliza estado, String canon, int meses) {
        Poliza p = new Poliza();
        p.setId(id);
        p.setTipo(tipo);
        p.setEstado(estado);
        p.setFechaInicio(LocalDate.of(2026, 1, 1));
        p.setVigenciaMeses(meses);
        BigDecimal canonMensual = new BigDecimal(canon);
        p.setCanonMensual(canonMensual);
        p.setPrima(canonMensual.multiply(BigDecimal.valueOf(meses)));
        return p;
    }

    public static Riesgo riesgo(Long id, Poliza poliza, EstadoRiesgo estado) {
        Riesgo r = new Riesgo();
        r.setId(id);
        r.setPoliza(poliza);
        r.setArrendatario("Arrendatario " + id);
        r.setDireccionInmueble("Calle " + id);
        r.setEstado(estado);
        poliza.getRiesgos().add(r);
        return r;
    }
}
