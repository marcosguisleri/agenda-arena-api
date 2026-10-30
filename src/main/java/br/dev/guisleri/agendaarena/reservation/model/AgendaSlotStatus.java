package br.dev.guisleri.agendaarena.reservation.model;

/**
 * Situação de um horário em uma data, do ponto de vista do administrador.
 * Precedência: RESERVED, BLOCKED, RECURRING, FREE.
 */
public enum AgendaSlotStatus {
    FREE,
    RESERVED,
    RECURRING,
    BLOCKED
}
