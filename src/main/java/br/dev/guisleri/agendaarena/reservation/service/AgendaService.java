package br.dev.guisleri.agendaarena.reservation.service;

import br.dev.guisleri.agendaarena.reservation.dto.AgendaSlotResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.AgendaSlotResponseDTO.AgendaRecurringReservationDTO;
import br.dev.guisleri.agendaarena.reservation.dto.ReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.dto.SlotBlockResponseDTO;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import br.dev.guisleri.agendaarena.reservation.model.AgendaSlotStatus;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.service.SportFieldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agenda do administrador: todos os horários de um campo em uma data, com o que ocupa cada um.
 * É uma leitura, então não usa o lock do slot. Faz uma consulta por tipo (reservas, bloqueios,
 * recorrências, liberações) para o campo inteiro, sem N+1.
 */
@Service
public class AgendaService {

    private final SportFieldService sportFieldService;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final ReservationRepository reservationRepository;
    private final SlotBlockRepository slotBlockRepository;
    private final RecurringReservationRepository recurringReservationRepository;
    private final RecurringReservationReleaseRepository recurringReservationReleaseRepository;

    public AgendaService(
            SportFieldService sportFieldService,
            ScheduleSlotRepository scheduleSlotRepository,
            ReservationRepository reservationRepository,
            SlotBlockRepository slotBlockRepository,
            RecurringReservationRepository recurringReservationRepository,
            RecurringReservationReleaseRepository recurringReservationReleaseRepository
    ) {
        this.sportFieldService = sportFieldService;
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.reservationRepository = reservationRepository;
        this.slotBlockRepository = slotBlockRepository;
        this.recurringReservationRepository = recurringReservationRepository;
        this.recurringReservationReleaseRepository = recurringReservationReleaseRepository;
    }

    @Transactional(readOnly = true)
    public List<AgendaSlotResponseDTO> findAgenda(
            Long establishmentId,
            Long sportFieldId,
            LocalDate date
    ) {
        sportFieldService.findSportFieldById(establishmentId, sportFieldId);

        List<ScheduleSlot> scheduleSlots =
                scheduleSlotRepository
                        .findAllBySportFieldIdAndDayOfWeek(
                                sportFieldId,
                                date.getDayOfWeek()
                        )
                        .stream()
                        .sorted(Comparator.comparing(ScheduleSlot::getStartTime))
                        .toList();

        if (scheduleSlots.isEmpty()) {
            return List.of();
        }

        Map<Long, Reservation> reservationsBySlotId =
                reservationRepository
                        .findAllByScheduleSlotSportFieldIdAndReservationDateAndStatusIn(
                                sportFieldId,
                                date,
                                ReservationStatus.OCCUPYING
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                reservation -> reservation.getScheduleSlot().getId(),
                                Function.identity()
                        ));

        Map<Long, SlotBlock> blocksBySlotId =
                slotBlockRepository
                        .findAllByScheduleSlotSportFieldIdAndBlockDate(sportFieldId, date)
                        .stream()
                        .collect(Collectors.toMap(
                                block -> block.getScheduleSlot().getId(),
                                Function.identity()
                        ));

        List<RecurringReservation> recurringReservations =
                recurringReservationRepository.findAllCoveringDateBySportField(
                        sportFieldId,
                        date.getDayOfWeek(),
                        date
                );

        Set<Long> releasedRecurringReservationIds = new HashSet<>();

        if (!recurringReservations.isEmpty()) {
            releasedRecurringReservationIds.addAll(
                    recurringReservationReleaseRepository
                            .findReleasedRecurringReservationIds(
                                    recurringReservations.stream()
                                            .map(RecurringReservation::getId)
                                            .toList(),
                                    date
                            )
            );
        }

        Map<Long, RecurringReservation> recurringBySlotId =
                recurringReservations.stream()
                        .collect(Collectors.toMap(
                                recurring -> recurring.getScheduleSlot().getId(),
                                Function.identity()
                        ));

        return scheduleSlots.stream()
                .map(scheduleSlot -> toAgendaSlot(
                        scheduleSlot,
                        date,
                        reservationsBySlotId.get(scheduleSlot.getId()),
                        blocksBySlotId.get(scheduleSlot.getId()),
                        recurringBySlotId.get(scheduleSlot.getId()),
                        releasedRecurringReservationIds
                ))
                .toList();
    }

    private AgendaSlotResponseDTO toAgendaSlot(
            ScheduleSlot scheduleSlot,
            LocalDate date,
            Reservation reservation,
            SlotBlock block,
            RecurringReservation recurring,
            Set<Long> releasedRecurringReservationIds
    ) {
        boolean released = recurring != null
                && releasedRecurringReservationIds.contains(recurring.getId());

        AgendaSlotStatus status;

        if (reservation != null) {
            status = AgendaSlotStatus.RESERVED;
        } else if (block != null) {
            status = AgendaSlotStatus.BLOCKED;
        } else if (recurring != null && !released) {
            status = AgendaSlotStatus.RECURRING;
        } else {
            status = AgendaSlotStatus.FREE;
        }

        return new AgendaSlotResponseDTO(
                scheduleSlot.getId(),
                scheduleSlot.isActive(),
                date,
                scheduleSlot.getStartTime(),
                scheduleSlot.getEndTime(),
                scheduleSlot.getPrice(),
                status,
                reservation == null
                        ? null
                        : ReservationResponseDTO.fromEntity(reservation),
                block == null
                        ? null
                        : SlotBlockResponseDTO.fromEntity(block),
                recurring == null
                        ? null
                        : toRecurringDTO(recurring, released)
        );
    }

    private AgendaRecurringReservationDTO toRecurringDTO(
            RecurringReservation recurring,
            boolean released
    ) {
        return new AgendaRecurringReservationDTO(
                recurring.getId(),
                recurring.getOrganizer() == null
                        ? null
                        : recurring.getOrganizer().getId(),
                recurring.getResponsibleName(),
                recurring.getResponsiblePhone(),
                recurring.getStartDate(),
                recurring.getEndDate(),
                released
        );
    }
}
