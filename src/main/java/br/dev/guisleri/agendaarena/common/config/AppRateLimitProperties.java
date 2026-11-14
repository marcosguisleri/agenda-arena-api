package br.dev.guisleri.agendaarena.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de requisições, lidos de {@code app.rate-limit.*}. Cada regra é "até {@code limit}
 * requisições a cada {@code windowSeconds} segundos" (balde de fichas: o limite também é o
 * tamanho da rajada). Os valores têm padrão; só informe os que quiser mudar, sempre o par
 * {@code limit} e {@code window-seconds} de uma regra.
 *
 * @param enabled               liga a limitação (os testes de regra de negócio a desligam)
 * @param trustForwardedHeaders usa o {@code X-Forwarded-For} (entrada mais à direita, a do nosso
 *                              proxy) como IP do cliente. Só ligue se a API for alcançável
 *                              <b>apenas</b> pelo proxy, senão o cliente forja o cabeçalho
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record AppRateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean trustForwardedHeaders,
        Rule loginIp,
        Rule loginAccount,
        Rule refresh,
        Rule signup,
        Rule matchView,
        Rule matchJoinIp,
        Rule matchJoinLink,
        Rule catalog
) {

    public AppRateLimitProperties {
        loginIp = loginIp != null ? loginIp : new Rule(10, 60);
        loginAccount = loginAccount != null ? loginAccount : new Rule(5, 300);
        refresh = refresh != null ? refresh : new Rule(30, 60);
        signup = signup != null ? signup : new Rule(10, 3600);
        matchView = matchView != null ? matchView : new Rule(60, 60);
        matchJoinIp = matchJoinIp != null ? matchJoinIp : new Rule(10, 60);
        matchJoinLink = matchJoinLink != null ? matchJoinLink : new Rule(60, 3600);
        catalog = catalog != null ? catalog : new Rule(120, 60);
    }

    public record Rule(int limit, int windowSeconds) {

        public Rule {
            if (limit < 1 || windowSeconds < 1) {
                throw new IllegalArgumentException(
                        "Rate limit rules need limit and window-seconds greater than zero"
                );
            }
        }
    }
}
