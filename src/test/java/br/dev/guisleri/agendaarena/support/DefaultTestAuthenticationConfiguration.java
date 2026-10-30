package br.dev.guisleri.agendaarena.support;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Faz todo MockMvc autoconfigurado dos testes de integração "pré-segurança" chamar a API como
 * PLATFORM_ADMIN, que enxerga tudo. Assim esses testes continuam testando regra de negócio, sem
 * repetir autenticação em cada requisição.
 * <p>
 * Os testes de segurança NÃO usam esse MockMvc: eles montam o próprio (sem usuário padrão) e usam
 * tokens reais.
 */
@Configuration
public class DefaultTestAuthenticationConfiguration {

    @Bean
    public MockMvcBuilderCustomizer defaultPlatformAdminCustomizer() {
        return builder -> builder.defaultRequest(
                get("/").with(
                        jwt()
                                .jwt(token -> token
                                        .subject("0")
                                        .claim("roles", List.of("PLATFORM_ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                )
        );
    }

}
