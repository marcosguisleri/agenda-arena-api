package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties;
import br.dev.guisleri.agendaarena.common.config.SecurityConfig;
import br.dev.guisleri.agendaarena.common.exception.TooManyRequestsException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Limita as rotas públicas por IP e por link de partida, antes da autenticação. Negada, a
 * requisição vira 429 pelo mesmo {@code GlobalExceptionHandler} das demais rotas (com
 * {@code Retry-After}). Roda depois do CORS para a resposta 429 também levar os cabeçalhos CORS.
 * Rotas autenticadas não são limitadas aqui (dívida registrada).
 * <p>
 * Não é um bean: é criado na cadeia de segurança, para o Spring Boot não registrá-lo também como
 * filtro global do servlet (rodaria duas vezes).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver exceptionResolver;
    private final List<Limit> limits = new ArrayList<>();

    public RateLimitFilter(
            TokenBucketRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            AppRateLimitProperties properties,
            HandlerExceptionResolver exceptionResolver
    ) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.exceptionResolver = exceptionResolver;

        post("login-ip", properties.loginIp(), false, "/auth/login");
        post("refresh-ip", properties.refresh(), false, "/auth/refresh", "/auth/logout");
        post("signup-ip", properties.signup(), false, "/users");
        get("match-view-ip", properties.matchView(), false,
                "/matches/{publicToken}", "/matches/{publicToken}/summary");
        post("match-join-ip", properties.matchJoinIp(), false,
                "/matches/{publicToken}/participants");
        post("match-join-link", properties.matchJoinLink(), true,
                "/matches/{publicToken}/participants");
        get("catalog-ip", properties.catalog(), false, SecurityConfig.PUBLIC_GET_PATTERNS);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        PathContainer path = PathContainer.parsePath(
                request.getRequestURI().substring(request.getContextPath().length())
        );
        String clientIp = null;

        for (Limit limit : limits) {
            if (!limit.method.matches(request.getMethod())) {
                continue;
            }

            PathPattern.PathMatchInfo match = limit.pattern.matchAndExtract(path);

            if (match == null) {
                continue;
            }

            if (clientIp == null) {
                clientIp = clientIpResolver.resolve(request);
            }

            String subject = limit.byLink ? match.getUriVariables().get("publicToken") : clientIp;
            RateLimitDecision decision = rateLimiter.tryConsume(
                    limit.name + ":" + subject,
                    limit.rule
            );

            if (!decision.allowed()) {
                exceptionResolver.resolveException(
                        request,
                        response,
                        null,
                        new TooManyRequestsException(decision.retryAfterSeconds())
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void post(String name, AppRateLimitProperties.Rule rule, boolean byLink,
                      String... patterns) {
        add(HttpMethod.POST, name, rule, byLink, patterns);
    }

    private void get(String name, AppRateLimitProperties.Rule rule, boolean byLink,
                     String... patterns) {
        add(HttpMethod.GET, name, rule, byLink, patterns);
    }

    private void add(HttpMethod method, String name, AppRateLimitProperties.Rule rule,
                     boolean byLink, String... patterns) {
        for (String pattern : patterns) {
            limits.add(new Limit(
                    method,
                    name,
                    rule,
                    byLink,
                    PathPatternParser.defaultInstance.parse(pattern)
            ));
        }
    }

    private record Limit(
            HttpMethod method,
            String name,
            AppRateLimitProperties.Rule rule,
            boolean byLink,
            PathPattern pattern
    ) {
    }
}
