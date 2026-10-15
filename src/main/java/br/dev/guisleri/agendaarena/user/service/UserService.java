package br.dev.guisleri.agendaarena.user.service;

import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.exception.InvalidPasswordException;
import br.dev.guisleri.agendaarena.user.exception.UserAlreadyExistsException;
import br.dev.guisleri.agendaarena.user.exception.UserNotFoundException;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(
            String name,
            String email,
            String phone,
            String password
    ) {

        String normalizedName = name.trim();

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(
                    "User with email " + normalizedEmail + " already exists"
            );
        }

        String normalizedPhone = phone.replaceAll("\\D", "");

        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;

        if (passwordBytes > 72)
            throw new InvalidPasswordException("Password must not exceed 72 bytes");

        String passwordHash = passwordEncoder.encode(password);

        User user = new User(
                normalizedName,
                normalizedEmail,
                normalizedPhone,
                passwordHash
        );

        return userRepository.save(user);

    }

    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(
                        "User with id " + id + " not found"
                ));
    }

}
