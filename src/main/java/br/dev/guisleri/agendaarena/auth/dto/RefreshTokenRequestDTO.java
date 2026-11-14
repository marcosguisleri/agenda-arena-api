package br.dev.guisleri.agendaarena.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequestDTO(

        @NotBlank(message = "Refresh token is required")
        @Size(max = 128, message = "Refresh token must have at most 128 characters")
        String refreshToken

) {
}
