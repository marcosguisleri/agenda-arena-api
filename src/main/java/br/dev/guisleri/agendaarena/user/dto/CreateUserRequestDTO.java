package br.dev.guisleri.agendaarena.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequestDTO(

        @NotBlank(message = "Name is required")
        @Size(
                max = 120,
                message = "Name must have at most 120 characters"
        )
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(
                max = 180,
                message = "Email must have at most 180 characters"
        )
        String email,

        @NotBlank(message = "Phone is required")
        @Size(
                max = 20,
                message = "Phone must have at most 20 characters"
        )
        @Pattern(
                regexp = "^(?=(?:\\D*\\d){10,11}\\D*$)[0-9().\\s-]+$",
                message = "Phone must contain 10 or 11 digits"
        )
        String phone,

        @NotBlank(message = "Password is required")
        @Size(
                min = 8,
                message = "Password must have at least 8 characters"
        )
        String password

) {
}
