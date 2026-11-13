package com.brandon.polizas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Avisa al tomador de la poliza por correo y SMS.
 * En este ejercicio el envio se simula escribiendo en el log; en produccion llamaria a un proveedor real.
 */
@Slf4j
@Service
public class NotificacionService {

    /**
     * Envia un aviso al tomador de la poliza.
     *
     * @param polizaId poliza sobre la que se avisa
     * @param mensaje  texto del aviso
     */
    public void notificarTomador(Long polizaId, String mensaje) {
        log.info("[NOTIFICACION] Correo y SMS al tomador de la poliza {}: {}", polizaId, mensaje);
    }
}
