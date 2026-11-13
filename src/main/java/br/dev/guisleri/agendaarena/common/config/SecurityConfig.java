package br.dev.guisleri.agendaarena.common.config;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.common.security.EstablishmentAccessAuthorization;
import br.dev.guisleri.agendaarena.common.security.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Segurança da API: JWT stateless (Bearer) e isolamento entre estabelecimentos.
 * <p>
 * Regra de ouro: tudo é NEGADO por padrão. Uma rota nova só fica acessível se uma regra abaixo a
 * liberar, e qualquer rota nova sob {@code /establishments/{establishmentId}/**} já nasce
 * restrita aos administradores daquele estabelecimento.
 */
@Configuration
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    /**
     * Catálogo público: leitura de estabelecimentos, quadras, horários e disponibilidade.
     * Não contém dados de clientes nem de reservas.
     */
    private static final String[] PUBLIC_GET_PATTERNS = {
            "/establishments",
            "/establishments/{establishmentId}",
            "/establishments/{establishmentId}/sports-fields",
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}",
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability",
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots",
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}"
    };

    /**
     * Documentação da API. Em produção, desligue com API_DOCS_ENABLED=false (as rotas deixam de
     * existir; esta liberação passa a não ter efeito).
     */
    private static final String[] API_DOCS_PATTERNS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private static final String ONLINE_RESERVATION_PATTERN =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityErrorHandler errorHandler,
            EstablishmentAccessAuthorization establishmentAccess
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Públicas
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/users").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, API_DOCS_PATTERNS).permitAll()

                        // Página da partida pelo link compartilhado (token aleatório, sem conta)
                        .requestMatchers(HttpMethod.GET, "/matches/{publicToken}").permitAll()

                        // Só a plataforma (dona do sistema)
                        .requestMatchers(HttpMethod.POST, "/establishments")
                        .hasRole(AuthenticatedUsers.PLATFORM_ADMIN)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/establishments/{establishmentId}/status"
                        )
                        .hasRole(AuthenticatedUsers.PLATFORM_ADMIN)
                        .requestMatchers(
                                HttpMethod.POST,
                                "/establishments/{establishmentId}/admins"
                        )
                        .hasRole(AuthenticatedUsers.PLATFORM_ADMIN)

                        // Área do usuário logado
                        .requestMatchers("/me/establishments")
                        .hasAnyRole(
                                AuthenticatedUsers.ESTABLISHMENT_ADMIN,
                                AuthenticatedUsers.PLATFORM_ADMIN
                        )
                        .requestMatchers("/me/**").authenticated()

                        // Reserva online: organizador (para si) ou administrador do estabelecimento
                        .requestMatchers(HttpMethod.POST, ONLINE_RESERVATION_PATTERN)
                        .access(establishmentAccess.organizerOrTenantAdmin())

                        // Todo o resto do estabelecimento: só administradores dele
                        .requestMatchers("/establishments/{establishmentId}/**")
                        .access(establishmentAccess.tenantAdmin())

                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
