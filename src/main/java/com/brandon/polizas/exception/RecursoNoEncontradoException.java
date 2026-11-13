package com.brandon.polizas.exception;

/** La poliza o el riesgo solicitado no existe. Se responde con HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
