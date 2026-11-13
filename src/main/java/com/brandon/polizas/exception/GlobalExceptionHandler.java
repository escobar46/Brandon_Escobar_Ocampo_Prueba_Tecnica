package com.brandon.polizas.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/** Traduce las excepciones a respuestas HTTP con el formato {@link ApiError}, en un solo lugar. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** La poliza o el riesgo no existe. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError noEncontrado(RecursoNoEncontradoException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Se violo una regla de negocio (por ejemplo, renovar una poliza cancelada). */
    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError reglaNegocio(ReglaNegocioException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Dos operaciones modificaron el mismo registro al mismo tiempo; la segunda se rechaza. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError modificacionSimultanea(OptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT,
                "El registro fue modificado por otra operacion al mismo tiempo; consulte de nuevo e intente otra vez");
    }

    /** El body no cumple las validaciones; el mensaje indica que campo fallo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, detalle);
    }

    /** JSON mal formado o un valor de filtro invalido (por ejemplo {@code tipo=XYZ}). */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError peticionInvalida(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Peticion invalida: revise los valores y el formato enviado");
    }

    private ApiError error(HttpStatus status, String mensaje) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), mensaje);
    }
}
