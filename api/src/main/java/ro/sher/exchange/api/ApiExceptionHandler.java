package ro.sher.exchange.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns exceptions into HTTP errors with a JSON body in the standard "problem details" format (RFC 9457).
// It also catches errors from the engine: a failed future reaches these handlers like a normal exception.
@RestControllerAdvice
public class ApiExceptionHandler{
    // the engine and the domain classes validate with IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownSymbolException.class)
    public ProblemDetail unknownSymbol(UnknownSymbolException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // SymbolEngine fails commands with IllegalStateException when it is stopping
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail engineUnavailable(IllegalStateException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
