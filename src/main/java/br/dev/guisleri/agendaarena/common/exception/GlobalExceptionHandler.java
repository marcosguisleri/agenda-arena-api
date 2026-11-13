package br.dev.guisleri.agendaarena.common.exception;

import br.dev.guisleri.agendaarena.auth.exception.InvalidCredentialsException;
import br.dev.guisleri.agendaarena.availability.exception.AvailabilityNotAllowedException;
import br.dev.guisleri.agendaarena.establishment.exception.EstablishmentNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.FieldPlayerLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.GoalkeeperLimitReachedException;
import br.dev.guisleri.agendaarena.match.exception.MatchInvalidStateException;
import br.dev.guisleri.agendaarena.match.exception.MatchNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchParticipantNotFoundException;
import br.dev.guisleri.agendaarena.match.exception.MatchRegistrationNotAllowedException;
import br.dev.guisleri.agendaarena.match.exception.ParticipantPaymentConflictException;
import br.dev.guisleri.agendaarena.match.exception.ReservationNotEligibleForMatchException;
import br.dev.guisleri.agendaarena.match.exception.TeamShirtColorConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationInvalidStateException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleReservationNotFoundException(
            ReservationNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ReservationInvalidStateException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleReservationInvalidStateException(
            ReservationInvalidStateException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MatchNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMatchNotFoundException(
            MatchNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MatchParticipantNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMatchParticipantNotFoundException(
            MatchParticipantNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MatchRegistrationNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMatchRegistrationNotAllowedException(
            MatchRegistrationNotAllowedException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(GoalkeeperLimitReachedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleGoalkeeperLimitReachedException(
            GoalkeeperLimitReachedException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(FieldPlayerLimitReachedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleFieldPlayerLimitReachedException(
            FieldPlayerLimitReachedException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(MatchInvalidStateException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMatchInvalidStateException(
            MatchInvalidStateException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ParticipantPaymentConflictException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleParticipantPaymentConflictException(
            ParticipantPaymentConflictException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(TeamShirtColorConflictException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleTeamShirtColorConflictException(
            TeamShirtColorConflictException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ReservationNotEligibleForMatchException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleReservationNotEligibleForMatchException(
            ReservationNotEligibleForMatchException exception
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

    @ExceptionHandler(RecurringReservationConflictException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleRecurringReservationConflictException(
            RecurringReservationConflictException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(RecurringReservationNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleRecurringReservationNotAllowedException(
            RecurringReservationNotAllowedException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(RecurringReservationNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleRecurringReservationNotFoundException(
            RecurringReservationNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(SlotBlockNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleSlotBlockNotFoundException(
            SlotBlockNotFoundException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(SlotBlockConflictException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleSlotBlockConflictException(
            SlotBlockConflictException exception
    ) {
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(SlotBlockNotAllowedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleSlotBlockNotAllowedException(
            SlotBlockNotAllowedException exception
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleInvalidCredentialsException(
            InvalidCredentialsException exception
    ) {
        return createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required",
                Map.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleAccessDeniedException(
            AccessDeniedException exception
    ) {
        return createErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                Map.of()
        );
    }

    @ExceptionHandler(ResourceNotAccessibleException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleResourceNotAccessibleException(
            ResourceNotAccessibleException exception
    ) {
        return createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                Map.of()
        );
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleValidationFailedException(
            ValidationFailedException exception
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of(exception.getField(), exception.getMessage())
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
