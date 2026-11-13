package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationInvalidStateException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotAllowedException;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.exception.ScheduleSlotNotFoundException;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.schedule.service.ScheduleSlotService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class ReservationService {

    private final UserService userService;
    private final ScheduleSlotService scheduleSlotService;
    private final SlotOccupancyService slotOccupancyService;
    private final ReservationRepository reservationRepository;
    private final SlotBlockService slotBlockService;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final Clock clock;

    public ReservationService(
            UserService userService,
            ScheduleSlotService scheduleSlotService, SlotOccupancyService slotOccupancyService,
            ReservationRepository reservationRepository,
            SlotBlockService slotBlockService,
            ScheduleSlotRepository scheduleSlotRepository,
            Clock clock
    ) {
        this.userService = userService;
        this.scheduleSlotService = scheduleSlotService;
        this.slotOccupancyService = slotOccupancyService;
        this.reservationRepository = reservationRepository;
        this.slotBlockService = slotBlockService;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.clock = clock;
    }

    @Transactional
    public Reservation createOnlineReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long organizerId,
            LocalDate reservationDate
    ) {

        User organizer = userService.findUserById(organizerId);

        if (!organizer.isActive()) {
            throw new ReservationNotAllowedException(
                    "Organizer is not active"
            );
        }

        ScheduleSlot scheduleSlot =
                findReservableScheduleSlot(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationDate
                );

        Reservation reservation =
                Reservation.online(
                        organizer,
                        scheduleSlot,
                        reservationDate
                );

        return saveReservation(reservation);
    }

    @Transactional
    public Reservation createManualReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate reservationDate,
            String customerName,
            String customerPhone
    ) {

        String normalizedName = customerName.trim();

        String normalizedPhone =
                customerPhone == null || customerPhone.isBlank()
                        ? null
                        : customerPhone.replaceAll("\\D", "");

        ScheduleSlot scheduleSlot =
                findReservableScheduleSlot(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId,
                        reservationDate
                );

        Reservation reservation =
                Reservation.manual(
                        scheduleSlot,
                        reservationDate,
                        normalizedName,
                        normalizedPhone
                );

        return saveReservation(reservation);
    }

    @Transactional
    public Reservation cancelReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        return cancelReservation(
                establishmentId,
                sportFieldId,
                scheduleSlotId,
                reservationId,
                false
        );
    }

    /**
     * Cancela uma reserva confirmada (ação administrativa). O horário volta a ficar disponível
     * porque o índice único parcial só considera reservas que ocupam o horário.
     * O lock do slot serializa o cancelamento com novas reservas e com outros cancelamentos.
     * Com {@code keepBlocked}, a data é bloqueada na mesma transação, para que o horário não
     * seja liberado ao público por engano.
     */
    @Transactional
    public Reservation cancelReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId,
            boolean keepBlocked
    ) {
        scheduleSlotService.findScheduleSlotForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        Reservation reservation =
                reservationRepository
                        .findByIdAndScheduleSlotId(reservationId, scheduleSlotId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        "Reservation with id " + reservationId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        reservation.cancel();

        Reservation cancelled = reservationRepository.saveAndFlush(reservation);

        if (keepBlocked) {
            slotBlockService.createBlock(
                    establishmentId,
                    sportFieldId,
                    scheduleSlotId,
                    cancelled.getReservationDate(),
                    "Reservation cancelled by the administrator"
            );
        }

        return cancelled;
    }

    @Transactional(readOnly = true)
    public Reservation findReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        scheduleSlotService.findScheduleSlotById(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        return reservationRepository
                .findByIdAndScheduleSlotId(reservationId, scheduleSlotId)
                .orElseThrow(() ->
                        new ReservationNotFoundException(
                                "Reservation with id " + reservationId
                                        + " not found for schedule slot "
                                        + scheduleSlotId
                        )
                );
    }

    // ---------- Área do organizador ("minhas reservas") ----------

    @Transactional(readOnly = true)
    public List<Reservation> findOrganizerReservations(Long organizerId) {
        return reservationRepository
                .findAllByOrganizerIdOrderByReservationDateDescStartTimeDesc(organizerId);
    }

    /**
     * Reserva de outro organizador é tratada como inexistente (404), para não revelar que existe.
     */
    @Transactional(readOnly = true)
    public Reservation findOrganizerReservation(Long organizerId, Long reservationId) {
        return reservationRepository
                .findByIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> reservationNotFound(reservationId));
    }

    /**
     * Reserva do organizador lida DEPOIS de travar o horário dela, para que o estado lido seja o
     * confirmado por quem tinha o lock. Usada por fluxos de fora do pacote (Partida).
     * Reserva de outro organizador: 404.
     */
    @Transactional
    public Reservation findOrganizerReservationForUpdate(Long organizerId, Long reservationId) {
        Long scheduleSlotId = reservationRepository
                .findScheduleSlotIdByIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> reservationNotFound(reservationId));
        Long sportFieldId = reservationRepository
                .findSportFieldIdByIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> reservationNotFound(reservationId));

        scheduleSlotRepository
                .findByIdAndSportFieldIdForUpdate(scheduleSlotId, sportFieldId)
                .orElseThrow(() -> new ScheduleSlotNotFoundException(
                        "Schedule slot with id " + scheduleSlotId + " not found"
                ));

        return findOrganizerReservation(organizerId, reservationId);
    }

    /**
     * O organizador pede o cancelamento da própria reserva. A reserva continua ocupando o horário
     * até o estabelecimento decidir. Trava o horário antes de ler a reserva para serializar com
     * as ações do administrador.
     */
    @Transactional
    public Reservation requestCancellation(Long organizerId, Long reservationId) {
        Long scheduleSlotId = reservationRepository
                .findScheduleSlotIdByIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> reservationNotFound(reservationId));
        Long sportFieldId = reservationRepository
                .findSportFieldIdByIdAndOrganizerId(reservationId, organizerId)
                .orElseThrow(() -> reservationNotFound(reservationId));

        scheduleSlotRepository
                .findByIdAndSportFieldIdForUpdate(scheduleSlotId, sportFieldId)
                .orElseThrow(() -> new ScheduleSlotNotFoundException(
                        "Schedule slot with id " + scheduleSlotId + " not found"
                ));

        Reservation reservation = findOrganizerReservation(organizerId, reservationId);

        ensureCancellationCanBeRequested(reservation);

        reservation.requestCancellation();

        return reservationRepository.saveAndFlush(reservation);
    }

    /**
     * O estabelecimento recusa o pedido de cancelamento: a reserva volta a ficar confirmada.
     * (Aprovar é o cancelamento administrativo normal, que aceita reserva com pedido pendente.)
     */
    @Transactional
    public Reservation refuseCancellationRequest(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        scheduleSlotService.findScheduleSlotForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        Reservation reservation =
                reservationRepository
                        .findByIdAndScheduleSlotId(reservationId, scheduleSlotId)
                        .orElseThrow(() -> reservationNotFound(reservationId));

        reservation.refuseCancellationRequest();

        return reservationRepository.saveAndFlush(reservation);
    }

    /**
     * Único ponto da regra de prazo para pedir cancelamento. Hoje: enquanto o horário não começou.
     * Quando o produto definir uma antecedência mínima (ex.: 24h), é só mudar aqui.
     */
    private void ensureCancellationCanBeRequested(Reservation reservation) {
        LocalDateTime startsAt = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        if (!startsAt.isAfter(LocalDateTime.now(clock))) {
            throw new ReservationInvalidStateException(
                    "Reservation has already started"
            );
        }
    }

    private static ReservationNotFoundException reservationNotFound(Long reservationId) {
        return new ReservationNotFoundException(
                "Reservation with id " + reservationId + " not found"
        );
    }

    /**
     * Marca a reserva como concluída (o jogo aconteceu). Só é permitido depois do início do horário.
     * A reserva continua ocupando o horário. Usa o lock do slot para serializar com cancelamentos.
     */
    @Transactional
    public Reservation completeReservation(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        Reservation reservation = findStartedReservationForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId,
                reservationId
        );

        reservation.complete();

        return reservationRepository.saveAndFlush(reservation);
    }

    /**
     * Marca a reserva como "não compareceu". Só é permitido depois do início do horário.
     * A reserva continua ocupando o horário.
     */
    @Transactional
    public Reservation markReservationAsNoShow(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        Reservation reservation = findStartedReservationForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId,
                reservationId
        );

        reservation.markNoShow();

        return reservationRepository.saveAndFlush(reservation);
    }

    private Reservation findStartedReservationForUpdate(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId
    ) {
        scheduleSlotService.findScheduleSlotForUpdate(
                establishmentId,
                sportFieldId,
                scheduleSlotId
        );

        Reservation reservation =
                reservationRepository
                        .findByIdAndScheduleSlotId(reservationId, scheduleSlotId)
                        .orElseThrow(() ->
                                new ReservationNotFoundException(
                                        "Reservation with id " + reservationId
                                                + " not found for schedule slot "
                                                + scheduleSlotId
                                )
                        );

        LocalDateTime startsAt = LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getStartTime()
        );

        if (startsAt.isAfter(LocalDateTime.now(clock))) {
            throw new ReservationInvalidStateException(
                    "Reservation has not started yet"
            );
        }

        return reservation;
    }

    private ScheduleSlot findReservableScheduleSlot(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            LocalDate reservationDate
    ) {

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate currentDate = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        if (reservationDate.isBefore(currentDate)) {
            throw new ReservationNotAllowedException(
                    "Reservation date cannot be in the past"
            );
        }

        ScheduleSlot scheduleSlot =
                scheduleSlotService.findBookableScheduleSlotForUpdate(
                        establishmentId,
                        sportFieldId,
                        scheduleSlotId
                );

        if (!reservationDate
                .getDayOfWeek()
                .equals(scheduleSlot.getDayOfWeek())) {

            throw new ReservationNotAllowedException(
                    "Reservation date does not match schedule slot day of week"
            );
        }

        if (reservationDate.equals(currentDate)
                && !scheduleSlot.getStartTime().isAfter(currentTime)) {

            throw new ReservationNotAllowedException(
                    "Reservation time cannot be in the past"
            );
        }

        if (slotOccupancyService.isBlocked(scheduleSlotId, reservationDate)) {
            throw new SlotBlockConflictException(
                    "Schedule slot is blocked for this date"
            );
        }

        boolean occupied =
                slotOccupancyService.isOccupied(
                        scheduleSlotId,
                        reservationDate
                );

        if (occupied) {
            throw new ReservationAlreadyExistsException(
                    "Schedule slot is already reserved for this date"
            );
        }

        return scheduleSlot;
    }

    private Reservation saveReservation(Reservation reservation) {

        try {
            return reservationRepository.saveAndFlush(reservation);

        } catch (DataIntegrityViolationException exception) {

            if (exception.getMessage() != null
                    && exception.getMessage()
                    .contains("idx_unique_active_reservation")) {

                throw new ReservationAlreadyExistsException(
                        "Schedule slot is already reserved for this date"
                );
            }

            throw exception;
        }
    }
}