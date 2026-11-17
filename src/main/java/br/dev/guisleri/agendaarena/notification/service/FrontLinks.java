package br.dev.guisleri.agendaarena.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Único lugar que monta os endereços do front que vão nos e-mails. A base vem de
 * {@code app.front-url} ({@code FRONT_URL}); os caminhos abaixo são o contrato com o front
 * (mudar uma rota do front = mudar aqui). Os links usam só a configuração e ids numéricos, nunca
 * texto vindo do usuário.
 */
@Component
public class FrontLinks {

    private final String baseUrl;

    public FrontLinks(@Value("${app.front-url:http://localhost:4200}") String frontUrl) {
        this.baseUrl = normalize(frontUrl);
    }

    /** Detalhe da reserva para o organizador dono dela. */
    public String organizerReservation(Long reservationId) {
        return baseUrl + "/me/reservations/" + reservationId;
    }

    /** Detalhe da reserva no painel do administrador do estabelecimento. */
    public String adminReservation(Long reservationId) {
        return baseUrl + "/admin/reservations/" + reservationId;
    }

    /**
     * Exige http(s) com host (a aplicação não sobe com valor inválido) e tira as barras do fim,
     * para os caminhos não saírem com "//".
     */
    private static String normalize(String frontUrl) {
        String trimmed = frontUrl == null ? "" : frontUrl.trim();
        URI uri;

        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.front-url is not a valid URL", exception);
        }

        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());

        if (!httpScheme || uri.getHost() == null) {
            throw new IllegalStateException("app.front-url must be an http(s) URL with a host");
        }

        return trimmed.replaceAll("/+$", "");
    }
}
