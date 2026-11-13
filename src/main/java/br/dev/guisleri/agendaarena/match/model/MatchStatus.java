package br.dev.guisleri.agendaarena.match.model;

public enum MatchStatus {
    /**
     * Inscrições abertas pelo link.
     */
    OPEN,
    /**
     * Lista e valores congelados (fatia 3).
     */
    CLOSED,
    /**
     * Partida encerrada, só resumo (fatia 4).
     */
    FINISHED
}
