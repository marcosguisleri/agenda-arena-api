package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByScheduleSlotIdAndReservationDateAndStatus(
            Long scheduleSlotId,
            LocalDate reservationDate,
            ReservationStatus status
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    List<Reservation> findAllByScheduleSlotSportFieldIdAndReservationDateAndStatus(
            Long sportFieldId,
            LocalDate reservationDate,
            ReservationStatus status
    );

}
