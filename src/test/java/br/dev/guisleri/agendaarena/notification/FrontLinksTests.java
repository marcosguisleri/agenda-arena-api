package br.dev.guisleri.agendaarena.notification;

import br.dev.guisleri.agendaarena.notification.service.FrontLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontLinksTests {

    @Test
    void shouldBuildTheLinksFromTheConfiguredBaseUrl() {
        FrontLinks links = new FrontLinks("https://app.agendaarena.com.br");

        assertThat(links.organizerReservation(7L))
                .isEqualTo("https://app.agendaarena.com.br/me/reservations/7");
        assertThat(links.adminReservation(7L))
                .isEqualTo("https://app.agendaarena.com.br/admin/reservations/7");
    }

    @ParameterizedTest(name = "base \"{0}\" não deixa barra dupla")
    @ValueSource(strings = {
            "http://localhost:4200/",
            "http://localhost:4200//",
            "  http://localhost:4200  "
    })
    void shouldNormalizeTrailingSlashesAndSpaces(String base) {
        assertThat(new FrontLinks(base).organizerReservation(1L))
                .isEqualTo("http://localhost:4200/me/reservations/1");
    }

    @ParameterizedTest(name = "\"{0}\" é recusada na subida")
    @ValueSource(strings = {"", "   ", "localhost:4200", "ftp://app.example.com",
            "javascript:alert(1)", "https://", "not a url"})
    void shouldRejectInvalidBaseUrl(String base) {
        assertThatThrownBy(() -> new FrontLinks(base))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.front-url");
    }

    @Test
    void shouldRejectNullBaseUrl() {
        assertThatThrownBy(() -> new FrontLinks(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
