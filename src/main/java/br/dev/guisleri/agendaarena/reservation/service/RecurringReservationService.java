package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationSlotKey;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotFoundException;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class RecurringReservationService {

    private final ReservationRepository reservationRepository;
    private final RecurringReservationRepository recurringReservationRepository;
    private final ScheduleSlotService scheduleSlotService;
    private final UserService userService;
    private final RecurringReservationReleaseRepository recurringReservationReleaseRepository;
    private final SlotBlockRepository slotBlockRepository;
    private final MatchRepository matchRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final Clock clock;

    public RecurringReservationService(
            ReservationRepository reservationRepository,
            RecurringReservationRepository recurringReservationRepository,
            ScheduleSlotService scheduleSlotService,
            UserService userService,
            RecurringReservationReleaseRepository recurringReservationReleaseRepository,
            SlotBlockRepository slotBlockRepository,
            MatchRepository matchRepository,
            ScheduleSlotRepository scheduleSlotRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.recurringReservationRepository = recurringReservationRepository;
        this.scheduleSlotService = scheduleSlotService;
        this.userService = userService;
        this.recurringReservationReleaseRepository = recurringReservationReleaseRepository;
        this.slotBlockRepository = slotBlockRepository;
        this.matchRepository = matchRepository;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.clock = clock;
    }

    @Transactional
    public RecurringReservation createRecurringReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long userId,
            String responsibleName,
            String responsiblePhone,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User organizer = userId != null
                ? userService.findUserById(userId)
                : null;

        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        RecurringReservation recurringReservation = new RecurringReservation(
                scheduleSlot,
                organizer,
                responsibleName,
                responsiblePhone,
                startDate,
                endDate
        );

        boolean hasConflict;

        if (endDate == null) {
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusInAndReservationDateGreaterThanEqual(
                    scheduleSlotId,
                    ReservationStatus.OCCUPYING,
                    startDate
            );
        } else {
            hasConflict = reservationRepository.existsByScheduleSlotIdAndStatusInAndReservationDateBetween(
                    scheduleSlotId,
                    ReservationStatus.OCCUPYING,
                    startDate,
                    endDate
            );
        }

        if (hasConflict) {
            throw new RecurringReservationConflictException("There are already confirmed reservations for that period, which is causing a conflict.");
        }

        boolean blockConflict;

        if (endDate == null) {
            blockConflict = slotBlockRepository.existsByScheduleSlotIdAndBlockDateGreaterThanEqual(
                    scheduleSlotId,
                    startDate
            );
        } else {
            blockConflict = slotBlockRepository.existsByScheduleSlotIdAndBlockDateBetween(
                    scheduleSlotId,
                    startDate,
                    endDate
            );
        }

        if (blockConflict) {
            throw new RecurringReservationConflictException(
                    "There are blocked dates in that period, which is causing a conflict."
            );
        }

        boolean recurringConflict =
                recurringReservationRepository.existsOverlapping(
                        scheduleSlotId,
                        startDate,
                        endDate
                );

        if (recurringConflict) {
            throw new RecurringReservationConflictException(
                    "There is already a recurring reservation for that period."
            );
        }

        return saveRecurringReservation(recurringReservation);
    }

    /**
     * Libera uma única ocorrência de uma recorrência, sem excluir a série.
     * Segue o mesmo protocolo das demais reservas: lock pessimista do slot primeiro, validações depois.
     * Assim, liberar e reservar a mesma data são serializados e nunca disputam o mesmo estado.
     */
    @Transactional
    public RecurringReservationRelease releaseOccurrence(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long recurringReservationId,
            LocalDate occurrenceDate
    ) {
        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        RecurringReservation recurringReservation =
                recurringReservationRepository
                        .findByIdAndScheduleSlotId(
                                recurringReservationId,
                                scheduleSlotId
                        )
                        .orElseThrow(() ->
                                new RecurringReservationNotFoundException(
                                        "Recurring reservation with id "
                                                + recurringReservationId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        validateOccurrenceCanBeReleased(
                recurringReservation,
                scheduleSlot,
                occurrenceDate
        );

        boolean alreadyReleased =
                recurringReservationReleaseRepository
                        .existsByRecurringReservationIdAndOccurrenceDate(
                                recurringReservationId,
                                occurrenceDate
                        );

        if (alreadyReleased) {
            throw new RecurringReservationConflictException(
                    "This occurrence has already been released."
            );
        }

        return saveRelease(
                new RecurringReservationRelease(
                        recurringReservation,
                        occurrenceDate,
                        Instant.now(clock)
                )
        );
    }

    /**
     * Séries do organizador (só as dele; o id vem do token).
     */
    @Transactional(readOnly = true)
    public List<RecurringReservation> findOrganizerRecurringReservations(Long organizerId) {
        return recurringReservationRepository
                .findAllByOrganizerIdOrderByStartDateDescIdDesc(organizerId);
    }

    /**
     * Série de outro organizador (ou sem organizador) é tratada como inexistente (404), para não
     * revelar que existe.
     */
    @Transactional(readOnly = true)
    public RecurringReservation findOrganizerRecurringReservation(
            Long organizerId,
            Long recurringReservationId
    ) {
        return recurringReservationRepository
                .findByIdAndOrganizerId(recurringReservationId, organizerId)
                .orElseThrow(() -> new RecurringReservationNotFoundException(
                        "Recurring reservation with id " + recurringReservationId + " not found"
                ));
    }

    /**
     * Série do organizador lida DEPOIS de travar o horário dela, para que o vínculo e o estado lidos
     * sejam os confirmados por quem tinha o lock (trocar o organizador também trava o slot). Série
     * de outro organizador: 404.
     * <p>
     * Exige transação aberta pelo chamador ({@code MANDATORY}): fora dela o lock seria liberado no
     * retorno e a série devolvida já não estaria protegida.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RecurringReservation findOrganizerRecurringReservationForUpdate(
            Long organizerId,
            Long recurringReservationId
    ) {
        ReservationSlotKey slotKey = recurringReservationRepository
                .findSlotKeyByIdAndOrganizerId(recurringReservationId, organizerId)
                .orElseThrow(() -> new RecurringReservationNotFoundException(
                        "Recurring reservation with id " + recurringReservationId + " not found"
                ));

        scheduleSlotRepository
                .findByIdAndSportFieldIdForUpdate(
                        slotKey.scheduleSlotId(),
                        slotKey.sportFieldId()
                )
                .orElseThrow(() -> new ScheduleSlotNotFoundException(
                        "Schedule slot with id " + slotKey.scheduleSlotId() + " not found"
                ));

        return findOrganizerRecurringReservation(organizerId, recurringReservationId);
    }

    /**
     * A data precisa ser uma ocorrência da série: dia da semana do slot e dentro do período.
     * (Não olha passado nem liberação: isso depende do uso.)
     */
    public void ensureDateIsOccurrenceOfSeries(
            RecurringReservation recurringReservation,
            LocalDate occurrenceDate
    ) {
        if (!occurrenceDate.getDayOfWeek()
                .equals(recurringReservation.getScheduleSlot().getDayOfWeek())) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date does not match schedule slot day of week"
            );
        }

        boolean beforeStart =
                occurrenceDate.isBefore(recurringReservation.getStartDate());

        boolean afterEnd =
                recurringReservation.getEndDate() != null
                        && occurrenceDate.isAfter(recurringReservation.getEndDate());

        if (beforeStart || afterEnd) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date is outside the recurring reservation period"
            );
        }
    }

    /**
     * O administrador vincula (ou troca) o organizador de uma série. Mesmo protocolo das demais
     * operações: lock do slot primeiro, validações depois. Trocar o organizador de uma série que
     * já tem partida é conflito (409): a partida guarda o dono copiado e ficaria divergente.
     */
    @Transactional
    public RecurringReservation linkOrganizer(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long recurringReservationId,
            Long organizerId
    ) {
        scheduleSlotService.findBookableScheduleSlotForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        RecurringReservation recurringReservation =
                recurringReservationRepository
                        .findByIdAndScheduleSlotId(recurringReservationId, scheduleSlotId)
                        .orElseThrow(() ->
                                new RecurringReservationNotFoundException(
                                        "Recurring reservation with id "
                                                + recurringReservationId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        User organizer = userService.findUserById(organizerId);

        if (!organizer.isActive()) {
            throw new RecurringReservationNotAllowedException("Organizer is not active");
        }

        // Quem gerencia a série e cria as partidas é um organizador: contas administrativas
        // (do estabelecimento ou da plataforma) não podem ser vinculadas por este caminho.
        if (organizer.getRole() != UserRole.ORGANIZER) {
            throw new RecurringReservationNotAllowedException("User is not an organizer");
        }

        User current = recurringReservation.getOrganizer();

        if (current != null && current.getId().equals(organizer.getId())) {
            return recurringReservation;
        }

        if (current != null
                && matchRepository.existsByRecurringReservationId(recurringReservationId)) {
            throw new RecurringReservationConflictException(
                    "The organizer cannot be changed because the recurring reservation already has matches."
            );
        }

        recurringReservation.linkOrganizer(organizer);

        return recurringReservationRepository.saveAndFlush(recurringReservation);
    }

    private void validateOccurrenceCanBeReleased(
            RecurringReservation recurringReservation,
            ScheduleSlot scheduleSlot,
            LocalDate occurrenceDate
    ) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();

        if (occurrenceDate.isBefore(currentDate)) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence date cannot be in the past"
            );
        }

        ensureDateIsOccurrenceOfSeries(recurringReservation, occurrenceDate);

        if (occurrenceDate.equals(currentDate)
                && !scheduleSlot.getStartTime().isAfter(now.toLocalTime())) {
            throw new RecurringReservationNotAllowedException(
                    "Occurrence time cannot be in the past"
            );
        }
    }

    private RecurringReservationRelease saveRelease(
            RecurringReservationRelease release
    ) {
        try {
            return recurringReservationReleaseRepository.saveAndFlush(release);

        } catch (DataIntegrityViolationException exception) {

            String message =
                    exception.getMostSpecificCause().getMessage();

            if (message != null
                    && message.contains(
                    "uk_recurring_reservations_releases_reservation_date"
            )) {

                throw new RecurringReservationConflictException(
                        "This occurrence has already been released."
                );
            }

            throw exception;
        }
    }

    private RecurringReservation saveRecurringReservation(
            RecurringReservation recurringReservation
    ) {
        try {
            return recurringReservationRepository.saveAndFlush(
                    recurringReservation
            );

        } catch (DataIntegrityViolationException exception) {

            String message =
                    exception.getMostSpecificCause().getMessage();

            if (message != null
                    && message.contains(
                    "ex_recurring_reservations_no_overlap"
            )) {

                throw new RecurringReservationConflictException(
                        "There is already a recurring reservation for that period."
                );
            }

            throw exception;
        }
    }

}
