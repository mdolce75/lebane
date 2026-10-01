package com.lebane.resilience;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Decide qué fallos se reintentan (Retry) y cuáles cuentan para abrir el circuito (CircuitBreaker). Se referencia
 * por nombre de clase desde {@code application.yml} ({@code retry-exception-predicate},
 * {@code record-exception-predicate}).
 *
 * <p>Transitorios: timeouts del TimeLimiter, errores de E/S de red y excepciones marcadas con
 * {@link TransientFailure}. El resto (p. ej. una respuesta 400 del proveedor o credenciales inválidas) no se
 * reintenta: repetirlo daría el mismo resultado y solo agregaría carga.
 */
public class TransientFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable instanceof IOException
                || throwable instanceof TransientFailure;
    }
}
