package com.brandon.polizas.exception;

/** La operacion viola una regla de negocio. Se responde con HTTP 409. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
