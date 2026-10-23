package br.dev.guisleri.agendaarena.common.exception;

import br.dev.guisleri.agendaarena.availability.exception.AvailabilityNotAllowedException;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotAllowedException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotAlreadyExistsException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotAllowedException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotBookableException;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotFoundException;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldAlreadyExistsException;
import br.dev.guisleri.agendaarena.sportfield.exception.SportFieldNotFoundException;
import br.dev.guisleri.agendaarena.user.exception.InvalidPasswordException;
import br.dev.guisleri.agendaarena.user.exception.UserAlreadyExistsException;
import br.dev.guisleri.agendaarena.user.exception.UserNotFoundException;
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

    @ExceptionHandler(ScheduleSlotAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleScheduleSlotAlreadyExistsException(
            ScheduleSlotAlreadyExistsException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ScheduleSlotNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleScheduleSlotNotFoundException(
            ScheduleSlotNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ScheduleSlotNotBookableException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleScheduleSlotNotBookableException(
            ScheduleSlotNotBookableException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ScheduleSlotNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleScheduleSlotNotAllowedException(
            ScheduleSlotNotAllowedException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleUserAlreadyExistsException(
            UserAlreadyExistsException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleUserNotFoundException(
            UserNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleInvalidPasswordException(
            InvalidPasswordException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ReservationAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleReservationAlreadyExistsException(
            ReservationAlreadyExistsException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ReservationNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleReservationNotAllowedException(
            ReservationNotAllowedException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(AvailabilityNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleAvailabilityNotAllowedException(
            AvailabilityNotAllowedException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
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
