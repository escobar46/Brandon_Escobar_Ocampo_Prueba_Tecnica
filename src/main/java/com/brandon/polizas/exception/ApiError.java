package com.brandon.polizas.exception;

import java.time.Instant;

/** Formato unico de error de toda la API: {@code { timestamp, status, error, mensaje }}. */
public record ApiError(Instant timestamp, int status, String error, String mensaje) {
}
