package br.dev.guisleri.agendaarena.user.controller;

import br.dev.guisleri.agendaarena.user.dto.CreateUserRequestDTO;
import br.dev.guisleri.agendaarena.user.dto.UserResponseDTO;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(
            @Valid @RequestBody CreateUserRequestDTO requestDTO
    ) {
        User user = userService.createUser(
                requestDTO.name(),
                requestDTO.email(),
                requestDTO.phone(),
                requestDTO.password()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponseDTO.fromEntity(user));
    }

}
