package com.brandon.polizas.service;

import com.brandon.polizas.dto.RiesgoRequest;
import com.brandon.polizas.dto.RiesgoResponse;
import com.brandon.polizas.exception.RecursoNoEncontradoException;
import com.brandon.polizas.exception.ReglaNegocioException;
import com.brandon.polizas.integration.CorePort;
import com.brandon.polizas.model.EstadoPoliza;
import com.brandon.polizas.model.EstadoRiesgo;
import com.brandon.polizas.model.Poliza;
import com.brandon.polizas.model.Riesgo;
import com.brandon.polizas.model.TipoPoliza;
import com.brandon.polizas.repository.PolizaRepository;
import com.brandon.polizas.repository.RiesgoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas de negocio de los riesgos: listar, agregar y cancelar.
 *
 * <p>Cada cambio se guarda con {@code saveAndFlush} antes de avisar al CORE: asi, si otra operacion
 * modifico el mismo registro al mismo tiempo (bloqueo optimista), falla en ese punto y no se avisa
 * de un cambio que no quedo guardado.
 */
@Service
public class RiesgoService {

    private final PolizaRepository polizaRepository;
    private final RiesgoRepository riesgoRepository;
    private final CorePort corePort;

    public RiesgoService(PolizaRepository polizaRepository,
                         RiesgoRepository riesgoRepository,
                         CorePort corePort) {
        this.polizaRepository = polizaRepository;
        this.riesgoRepository = riesgoRepository;
        this.corePort = corePort;
    }

    /**
     * Lista los riesgos de una poliza.
     *
     * @throws RecursoNoEncontradoException si la poliza no existe
     */
    @Transactional(readOnly = true)
    public List<RiesgoResponse> listarPorPoliza(Long polizaId) {
        buscarPoliza(polizaId);
        return riesgoRepository.findByPolizaId(polizaId).stream().map(RiesgoResponse::from).toList();
    }

    /**
     * Agrega un riesgo activo a una poliza colectiva.
     *
     * @throws RecursoNoEncontradoException si la poliza no existe
     * @throws ReglaNegocioException        si la poliza es individual (solo admite un riesgo) o esta cancelada
     */
    @Transactional
    public RiesgoResponse agregar(Long polizaId, RiesgoRequest request) {
        Poliza poliza = buscarPoliza(polizaId);

        if (poliza.getTipo() != TipoPoliza.COLECTIVA) {
            throw new ReglaNegocioException(
                    "Solo las polizas colectivas admiten agregar riesgos; una individual tiene un unico riesgo");
        }
        if (poliza.getEstado() == EstadoPoliza.CANCELADA) {
            throw new ReglaNegocioException("No se pueden agregar riesgos a una poliza cancelada");
        }

        Riesgo riesgo = new Riesgo();
        riesgo.setPoliza(poliza);
        riesgo.setArrendatario(request.arrendatario());
        riesgo.setDireccionInmueble(request.direccionInmueble());
        riesgo.setEstado(EstadoRiesgo.ACTIVO);
        riesgoRepository.saveAndFlush(riesgo);

        corePort.notificarEvento(CorePort.EVENTO_ACTUALIZACION, poliza.getId());
        return RiesgoResponse.from(riesgo);
    }

    /**
     * Cancela un riesgo.
     *
     * @throws RecursoNoEncontradoException si el riesgo no existe
     * @throws ReglaNegocioException        si el riesgo ya estaba cancelado
     */
    @Transactional
    public RiesgoResponse cancelar(Long riesgoId) {
        Riesgo riesgo = riesgoRepository.findById(riesgoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Riesgo " + riesgoId + " no encontrado"));

        if (riesgo.getEstado() == EstadoRiesgo.CANCELADO) {
            throw new ReglaNegocioException("El riesgo ya esta cancelado");
        }

        riesgo.setEstado(EstadoRiesgo.CANCELADO);
        riesgoRepository.saveAndFlush(riesgo);

        corePort.notificarEvento(CorePort.EVENTO_ACTUALIZACION, riesgo.getPoliza().getId());
        return RiesgoResponse.from(riesgo);
    }

    private Poliza buscarPoliza(Long id) {
        return polizaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Poliza " + id + " no encontrada"));
    }
}
