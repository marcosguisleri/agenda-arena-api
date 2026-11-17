package br.dev.guisleri.agendaarena.reservation.repository;

import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"scheduleSlot", "organizer"})
    Optional<Reservation> findByIdAndScheduleSlotId(Long id, Long scheduleSlotId);

    boolean existsByScheduleSlotIdAndReservationDateAndStatusIn(
            Long scheduleSlotId,
            LocalDate reservationDate,
            Collection<ReservationStatus> statuses
    );

    boolean existsByScheduleSlotIdAndStatusInAndReservationDateBetween(
            Long scheduleSlotId,
            Collection<ReservationStatus> statuses,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByScheduleSlotIdAndStatusInAndReservationDateGreaterThanEqual(
            Long scheduleSlotId,
            Collection<ReservationStatus> statuses,
            LocalDate startDate
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    List<Reservation> findAllByScheduleSlotSportFieldIdAndReservationDateAndStatusIn(
            Long sportFieldId,
            LocalDate reservationDate,
            Collection<ReservationStatus> statuses
    );

    /**
     * "Minhas reservas": mais recentes primeiro. O id desempata (data e horário se repetem entre
     * campos) para a paginação não repetir nem pular itens.
     */
    @EntityGraph(attributePaths = "scheduleSlot")
    Page<Reservation> findAllByOrganizerIdOrderByReservationDateDescStartTimeDescIdDesc(
            Long organizerId,
            Pageable pageable
    );

    /**
     * Listagem do administrador: reservas de um estabelecimento num período, com filtros
     * opcionais, em ordem fixa (data, horário, id). Slot, campo e organizador vêm na mesma consulta.
     */
    @EntityGraph(attributePaths = {"scheduleSlot", "scheduleSlot.sportField", "organizer"})
    @Query(value = """
            select r from Reservation r
            where r.scheduleSlot.sportField.establishment.id = :establishmentId
              and r.reservationDate between :from and :to
              and r.status in :statuses
              and (:sportFieldId is null or r.scheduleSlot.sportField.id = :sportFieldId)
              and (:origin is null or r.origin = :origin)
            order by r.reservationDate, r.startTime, r.id
            """,
            countQuery = """
            select count(r) from Reservation r
            where r.scheduleSlot.sportField.establishment.id = :establishmentId
              and r.reservationDate between :from and :to
              and r.status in :statuses
              and (:sportFieldId is null or r.scheduleSlot.sportField.id = :sportFieldId)
              and (:origin is null or r.origin = :origin)
            """)
    Page<Reservation> findForAdmin(
            @Param("establishmentId") Long establishmentId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("sportFieldId") Long sportFieldId,
            @Param("origin") ReservationOrigin origin,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "scheduleSlot")
    Optional<Reservation> findByIdAndOrganizerId(Long id, Long organizerId);

    /**
     * Consulta só de ids: permite travar o horário ANTES de carregar a reserva, para que o
     * estado lido já seja o confirmado por quem tinha o lock.
     */
    @Query("""
            select new br.dev.guisleri.agendaarena.reservation.repository.ReservationSlotKey(
                r.scheduleSlot.id, r.scheduleSlot.sportField.id
            )
            from Reservation r
            where r.id = :reservationId and r.organizer.id = :organizerId
            """)
    Optional<ReservationSlotKey> findSlotKeyByIdAndOrganizerId(
            @Param("reservationId") Long reservationId,
            @Param("organizerId") Long organizerId
    );

}
