package br.dev.guisleri.agendaarena.reservation.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    CONFIRMED,
    /**
     * O organizador pediu o cancelamento e aguarda a decisão do estabelecimento. A reserva
     * continua ocupando o horário até o administrador confirmar (cancelar) ou recusar.
     */
    CANCELLATION_REQUESTED,
    COMPLETED,
    NO_SHOW,
    CANCELLED;

    /**
     * Estados que ocupam o horário. Precisa ficar igual ao predicado do índice único parcial
     * {@code idx_unique_active_reservation} (migration V12).
     */
    public static final Set<ReservationStatus> OCCUPYING = Collections.unmodifiableSet(
            EnumSet.of(CONFIRMED, CANCELLATION_REQUESTED, COMPLETED, NO_SHOW)
    );
}
