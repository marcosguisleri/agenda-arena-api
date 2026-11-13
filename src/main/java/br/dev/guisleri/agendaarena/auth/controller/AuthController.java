package br.dev.guisleri.agendaarena.auth.controller;

import br.dev.guisleri.agendaarena.auth.dto.LoginRequestDTO;
import br.dev.guisleri.agendaarena.auth.dto.LoginResponseDTO;
import br.dev.guisleri.agendaarena.auth.service.AuthService;
import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
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
        JwtTokenService.IssuedToken token =
                authService.login(requestDTO.email(), requestDTO.password());

        return ResponseEntity.ok(
                new LoginResponseDTO(
                        token.accessToken(),
                        "Bearer",
                        token.expiresInSeconds()
                )
        );
    }
}
