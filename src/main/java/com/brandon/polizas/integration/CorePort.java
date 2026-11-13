package com.brandon.polizas.integration;

/**
 * Lo que el negocio necesita del CORE, sin depender de como se comunica (HTTP, SOAP, cola).
 * Los services usan esta interfaz; hoy la implementa {@link CoreHttpAdapter}.
 */
public interface CorePort {

    /** Evento que se envia en toda operacion que modifica el estado de una poliza o riesgo. */
    String EVENTO_ACTUALIZACION = "ACTUALIZACION";

    /**
     * Informa al CORE que una poliza cambio.
     * No lanza excepciones: si el CORE falla, el error se registra en el log y la operacion local sigue.
     */
    void notificarEvento(String evento, Long polizaId);
}
