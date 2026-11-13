package br.dev.guisleri.agendaarena.match.model;

/**
 * Sem estado "finalizada": retirado do escopo em 13/11/2026 (fechar já congela lista e valores).
 */
public enum MatchStatus {
    /**
     * Inscrições abertas pelo link; cotas recalculadas a cada mudança.
     */
    OPEN,
    /**
     * Lista e cotas congeladas; inscrições bloqueadas. Pode reabrir.
     */
    CLOSED
}
