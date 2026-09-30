package br.dev.guisleri.agendaarena.common.exception;

import java.util.Map;

public record ApiErrorResponseDTO(
        int status,
        String error,
        String message,
        Map<String, String> errors
) {
}
