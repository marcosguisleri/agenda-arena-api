package br.dev.guisleri.agendaarena.reservation.model;

/**
 * Situação de uma série recorrente em relação a hoje (não é gravada: depende da data).
 */
public enum RecurringReservationListStatus {
    /** Já começou e ainda não terminou (sem data final ou com data final de hoje em diante). */
    ACTIVE,
    /** Ainda não começou. */
    UPCOMING,
    /** Tem data final anterior a hoje. */
    ENDED
}
