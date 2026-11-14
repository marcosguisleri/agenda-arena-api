package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTests {

    private static ClientIpResolver resolver(boolean trustForwardedHeaders) {
        return new ClientIpResolver(
                new AppRateLimitProperties(
                        true, trustForwardedHeaders,
                        null, null, null, null, null, null, null, null));
    }

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");

        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        return request;
    }

    @Test
    @DisplayName("Por padrão usa o endereço da conexão e ignora o X-Forwarded-For")
    void ignoresForwardedHeaderByDefault() {
        assertThat(resolver(false).resolve(request("203.0.113.9"))).isEqualTo("10.0.0.7");
        assertThat(resolver(false).resolve(request(null))).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("Confiando no proxy, usa a última entrada (a do proxy), não as forjadas")
    void usesTheLastEntryWhenTrusted() {
        ClientIpResolver trusted = resolver(true);

        assertThat(trusted.resolve(request("203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(trusted.resolve(request("1.1.1.1, 2.2.2.2 ,203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("Cabeçalho ausente, vazio ou absurdo volta para o endereço da conexão")
    void fallsBackToTheConnectionAddress() {
        ClientIpResolver trusted = resolver(true);

        assertThat(trusted.resolve(request(null))).isEqualTo("10.0.0.7");
        assertThat(trusted.resolve(request("  "))).isEqualTo("10.0.0.7");
        assertThat(trusted.resolve(request("1.1.1.1, "))).isEqualTo("10.0.0.7");
        assertThat(trusted.resolve(request("x".repeat(200)))).isEqualTo("10.0.0.7");
    }
}
