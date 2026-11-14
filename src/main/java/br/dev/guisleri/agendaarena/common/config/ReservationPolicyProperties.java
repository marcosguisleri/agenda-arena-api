package br.dev.guisleri.agendaarena.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Janela de datas para reservar, lida de {@code app.reservation.*}.
 * <p>
 * Hoje valem para o sistema todo; quando houver SaaS, viram configuração por estabelecimento.
 *
 * @param maxAdvanceDays     até quantos dias à frente a reserva online e a consulta pública de
 *                           disponibilidade aceitam (a reserva manual não tem limite à frente)
 * @param manualMaxPastDays  até quantos dias atrás o admin pode lançar uma reserva manual
 *                           retroativa (ex.: agendamento feito por WhatsApp e esquecido)
 */
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationPolicyProperties(
        @DefaultValue("14") int maxAdvanceDays,
        @DefaultValue("30") int manualMaxPastDays
) {
}
