package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * IP do cliente para a limitação. Por padrão, o endereço da conexão. Com
 * {@code trust-forwarded-headers}, a <b>última</b> entrada do {@code X-Forwarded-For}: é a que o
 * nosso proxy acrescenta; as anteriores o cliente pode forjar. Só use atrás de um proxy que seja
 * o único caminho até a API.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_IP_LENGTH = 64;

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(AppRateLimitProperties properties) {
        this.trustForwardedHeaders = properties.trustForwardedHeaders();
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");

            if (forwarded != null && !forwarded.isBlank()) {
                String[] entries = forwarded.split(",");
                String last = entries[entries.length - 1].trim();

                if (!last.isEmpty() && last.length() <= MAX_IP_LENGTH) {
                    return last;
                }
            }
        }

        return request.getRemoteAddr();
    }
}
