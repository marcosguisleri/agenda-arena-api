package br.dev.guisleri.agendaarena.auth.service;

import br.dev.guisleri.agendaarena.auth.exception.InvalidCredentialsException;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * Hash usado para gastar o mesmo tempo quando o e-mail não existe, evitando revelar por
     * tempo de resposta quais e-mails têm conta.
     */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public JwtTokenService.IssuedToken login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        boolean passwordMatches =
                passwordEncoder.matches(password, user.getPasswordHash());

        if (!passwordMatches || !user.isActive()) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        return jwtTokenService.issue(user);
    }
}
