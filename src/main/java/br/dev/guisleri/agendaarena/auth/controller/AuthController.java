package br.dev.guisleri.agendaarena.auth.controller;

import br.dev.guisleri.agendaarena.auth.dto.LoginRequestDTO;
import br.dev.guisleri.agendaarena.auth.dto.LoginResponseDTO;
import br.dev.guisleri.agendaarena.auth.dto.RefreshTokenRequestDTO;
import br.dev.guisleri.agendaarena.auth.service.AuthService;
import br.dev.guisleri.agendaarena.auth.service.AuthTokens;
import br.dev.guisleri.agendaarena.common.ratelimit.ClientIpResolver;
import br.dev.guisleri.agendaarena.common.ratelimit.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthService authService,
            LoginAttemptLimiter loginAttemptLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.authService = authService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO requestDTO,
            HttpServletRequest request
    ) {
        loginAttemptLimiter.check(clientIpResolver.resolve(request), requestDTO.email());

        AuthTokens tokens =
                authService.login(requestDTO.email(), requestDTO.password());

        return ResponseEntity.ok(LoginResponseDTO.fromTokens(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO requestDTO
    ) {
        AuthTokens tokens = authService.refresh(requestDTO.refreshToken());

        return ResponseEntity.ok(LoginResponseDTO.fromTokens(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequestDTO requestDTO
    ) {
        authService.logout(requestDTO.refreshToken());

        return ResponseEntity.noContent().build();
    }
}
