package com.brandon.polizas.service;

import com.brandon.polizas.dto.PolizaResponse;
import com.brandon.polizas.exception.RecursoNoEncontradoException;
import com.brandon.polizas.exception.ReglaNegocioException;
import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.repository.PolizaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Reglas de negocio de las polizas: listar, renovar y cancelar.
 *
 * <p>Cada cambio se guarda con {@code saveAndFlush} antes de avisar al CORE: asi, si otra operacion
 * modifico el mismo registro al mismo tiempo (bloqueo optimista), falla en ese punto y no se avisa
 * de un cambio que no quedo guardado.
 */
@Service
public class PolizaService {

    private final PolizaRepository polizaRepository;
    private final CorePort corePort;
    private final NotificacionService notificacionService;
    private final BigDecimal ipc;

    public PolizaService(PolizaRepository polizaRepository,
                         CorePort corePort,
                         NotificacionService notificacionService,
                         @Value("${polizas.ipc}") BigDecimal ipc) {
        this.polizaRepository = polizaRepository;
        this.corePort = corePort;
        this.notificacionService = notificacionService;
        this.ipc = ipc;
    }

    /**
     * Lista las polizas, filtrando por tipo y/o estado si se indican.
     *
     * @param tipo   filtro opcional; {@code null} significa "todos los tipos"
     * @param estado filtro opcional; {@code null} significa "todos los estados"
     */
    @Transactional(readOnly = true)
    public List<PolizaResponse> listar(TipoPoliza tipo, EstadoPoliza estado) {
        List<Poliza> polizas;
        if (tipo != null && estado != null) {
            polizas = polizaRepository.findByTipoAndEstado(tipo, estado);
        } else if (tipo != null) {
            polizas = polizaRepository.findByTipo(tipo);
        } else if (estado != null) {
            polizas = polizaRepository.findByEstado(estado);
        } else {
            polizas = polizaRepository.findAll();
        }
        return polizas.stream().map(PolizaResponse::from).toList();
    }

    /**
     * Renueva una poliza por el mismo periodo de vigencia.
     * El canon sube segun el IPC (redondeado a 2 decimales), la prima se recalcula,
     * la fecha de inicio avanza un periodo y el estado pasa a RENOVADA.
     *
     * @throws RecursoNoEncontradoException si la poliza no existe
     * @throws ReglaNegocioException        si la poliza esta cancelada
     */
    @Transactional
    public PolizaResponse renovar(Long id) {
        Poliza poliza = buscarPoliza(id);

        if (poliza.getEstado() == EstadoPoliza.CANCELADA) {
            throw new ReglaNegocioException("No se puede renovar una poliza cancelada");
        }

        BigDecimal nuevoCanon = calcularCanonConIpc(poliza.getCanonMensual());
        poliza.setCanonMensual(nuevoCanon);
        poliza.setPrima(nuevoCanon.multiply(BigDecimal.valueOf(poliza.getVigenciaMeses())));
        poliza.setFechaInicio(poliza.getFechaInicio().plusMonths(poliza.getVigenciaMeses()));
        poliza.setEstado(EstadoPoliza.RENOVADA);
        polizaRepository.saveAndFlush(poliza);

        corePort.notificarEvento(CorePort.EVENTO_ACTUALIZACION, poliza.getId());
        notificacionService.notificarTomador(poliza.getId(), "Su poliza fue renovada. Nuevo canon mensual: "
                + poliza.getCanonMensual() + ", prima: " + poliza.getPrima());
        return PolizaResponse.from(poliza);
    }

    /**
     * Cancela una poliza y todos sus riesgos.
     *
     * @throws RecursoNoEncontradoException si la poliza no existe
     * @throws ReglaNegocioException        si la poliza ya estaba cancelada
     */
    @Transactional
    public PolizaResponse cancelar(Long id) {
        Poliza poliza = buscarPoliza(id);

        if (poliza.getEstado() == EstadoPoliza.CANCELADA) {
            throw new ReglaNegocioException("La poliza ya esta cancelada");
        }

        for (Riesgo riesgo : poliza.getRiesgos()) {
            riesgo.setEstado(EstadoRiesgo.CANCELADO);
        }
        poliza.setEstado(EstadoPoliza.CANCELADA);
        polizaRepository.saveAndFlush(poliza);

        corePort.notificarEvento(CorePort.EVENTO_ACTUALIZACION, poliza.getId());
        notificacionService.notificarTomador(poliza.getId(), "Su poliza fue cancelada junto con todos sus riesgos");
        return PolizaResponse.from(poliza);
    }

    /** canon nuevo = canon x (1 + IPC), redondeado a 2 decimales (HALF_UP). */
    private BigDecimal calcularCanonConIpc(BigDecimal canon) {
        return canon.multiply(BigDecimal.ONE.add(ipc)).setScale(2, RoundingMode.HALF_UP);
    }

    private Poliza buscarPoliza(Long id) {
        return polizaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Poliza " + id + " no encontrada"));
    }
}
