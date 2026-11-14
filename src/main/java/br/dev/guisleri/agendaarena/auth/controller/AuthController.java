package br.dev.guisleri.agendaarena.auth.controller;

import br.dev.guisleri.agendaarena.auth.dto.LoginRequestDTO;
import br.dev.guisleri.agendaarena.auth.dto.LoginResponseDTO;
import br.dev.guisleri.agendaarena.auth.dto.RefreshTokenRequestDTO;
import br.dev.guisleri.agendaarena.auth.service.AuthService;
import br.dev.guisleri.agendaarena.auth.service.AuthTokens;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO requestDTO
    ) {
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
