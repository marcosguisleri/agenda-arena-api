package br.dev.guisleri.agendaarena.common.security;

import br.dev.guisleri.agendaarena.common.exception.ResourceNotAccessibleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Entrega os erros de segurança (401, 403 e 404 de outro estabelecimento) pelo mesmo
 * {@code GlobalExceptionHandler} das demais rotas, para manter o formato de erro único.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver exceptionResolver;

    public SecurityErrorHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) {
        response.setHeader("WWW-Authenticate", "Bearer");
        exceptionResolver.resolveException(request, response, null, authException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) {
        Exception exception = accessDeniedException;

        if (Boolean.TRUE.equals(
                request.getAttribute(
                        EstablishmentAccessAuthorization.OTHER_ESTABLISHMENT_ATTRIBUTE
                )
        )) {
            exception = new ResourceNotAccessibleException("Resource not found");
        }

        exceptionResolver.resolveException(request, response, null, exception);
    }
}
