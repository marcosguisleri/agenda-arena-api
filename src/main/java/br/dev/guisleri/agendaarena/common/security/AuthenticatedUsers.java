package br.dev.guisleri.agendaarena.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class AuthenticatedUsers {

    public static final String ORGANIZER = "ORGANIZER";
    public static final String ESTABLISHMENT_ADMIN = "ESTABLISHMENT_ADMIN";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private AuthenticatedUsers() {
    }

    public static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public static boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }

        String authority = "ROLE_" + role;

        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    /**
     * Id do usuário autenticado (o {@code sub} do JWT) ou {@code null} se não for numérico.
     */
    public static Long userId(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
