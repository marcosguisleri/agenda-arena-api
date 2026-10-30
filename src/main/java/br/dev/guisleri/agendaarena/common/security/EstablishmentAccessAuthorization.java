package br.dev.guisleri.agendaarena.common.security;

import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Regras de acesso por estabelecimento (isolamento entre estabelecimentos).
 * <ul>
 *   <li>PLATFORM_ADMIN acessa qualquer estabelecimento.</li>
 *   <li>ESTABLISHMENT_ADMIN só acessa estabelecimentos aos quais está vinculado; nos demais o
 *       acesso é negado e marcado como "outro estabelecimento", o que resulta em 404.</li>
 *   <li>ORGANIZER nunca acessa rotas de gestão (403).</li>
 * </ul>
 * O vínculo é consultado no banco a cada requisição, então remover um administrador tem efeito
 * imediato, sem esperar o token expirar.
 */
@Component
public class EstablishmentAccessAuthorization {

    public static final String OTHER_ESTABLISHMENT_ATTRIBUTE =
            EstablishmentAccessAuthorization.class.getName() + ".OTHER_ESTABLISHMENT";

    private static final String ESTABLISHMENT_ID_VARIABLE = "establishmentId";

    private final EstablishmentAdminRepository establishmentAdminRepository;

    public EstablishmentAccessAuthorization(
            EstablishmentAdminRepository establishmentAdminRepository
    ) {
        this.establishmentAdminRepository = establishmentAdminRepository;
    }

    /**
     * Rotas de gestão de {@code /establishments/{establishmentId}/**}.
     */
    public AuthorizationManager<RequestAuthorizationContext> tenantAdmin() {
        return (authentication, context) ->
                decide(authentication.get(), context, false);
    }

    /**
     * Rotas que o organizador também usa (ex.: reservar online) mas que um administrador só
     * pode usar no próprio estabelecimento.
     */
    public AuthorizationManager<RequestAuthorizationContext> organizerOrTenantAdmin() {
        return (authentication, context) ->
                decide(authentication.get(), context, true);
    }

    private AuthorizationDecision decide(
            Authentication authentication,
            RequestAuthorizationContext context,
            boolean organizerAllowed
    ) {
        if (!AuthenticatedUsers.isAuthenticated(authentication)) {
            return new AuthorizationDecision(false);
        }

        if (AuthenticatedUsers.hasRole(authentication, AuthenticatedUsers.PLATFORM_ADMIN)) {
            return new AuthorizationDecision(true);
        }

        if (organizerAllowed
                && AuthenticatedUsers.hasRole(authentication, AuthenticatedUsers.ORGANIZER)) {
            return new AuthorizationDecision(true);
        }

        if (AuthenticatedUsers.hasRole(authentication, AuthenticatedUsers.ESTABLISHMENT_ADMIN)) {
            Long userId = AuthenticatedUsers.userId(authentication);
            Long establishmentId = parseId(
                    context.getVariables().get(ESTABLISHMENT_ID_VARIABLE)
            );

            boolean linked = userId != null
                    && establishmentId != null
                    && establishmentAdminRepository
                    .existsByUserIdAndEstablishmentId(userId, establishmentId);

            if (!linked) {
                context.getRequest().setAttribute(OTHER_ESTABLISHMENT_ATTRIBUTE, Boolean.TRUE);
            }

            return new AuthorizationDecision(linked);
        }

        return new AuthorizationDecision(false);
    }

    private static Long parseId(String value) {
        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
