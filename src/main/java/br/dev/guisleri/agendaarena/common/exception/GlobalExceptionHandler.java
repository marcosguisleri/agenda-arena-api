package br.dev.guisleri.agendaarena.common.exception;

import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldAlreadyExistsException;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EstablishmentNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleEstablishmentNotFoundException(
            EstablishmentNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(SportFieldNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleSportFieldNotFoundException(
            SportFieldNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(SportFieldAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleSportFieldAlreadyExistsException(
            SportFieldAlreadyExistsException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage(),
                        (first, second) -> first
                ));

        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request body",
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponseDTO> createErrorResponse(
            HttpStatus status,
            String message,
            Map<String, String> errors
    ) {
        ApiErrorResponseDTO errorResponse = new ApiErrorResponseDTO(
                status.value(),
                status.getReasonPhrase(),
                message,
                errors
        );

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
    }
}
