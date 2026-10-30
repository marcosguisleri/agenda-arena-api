package br.dev.guisleri.agendaarena.auth.service;

import br.dev.guisleri.agendaarena.common.config.AppSecurityProperties;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Cria o administrador da plataforma na subida da aplicação, a partir das variáveis de ambiente
 * PLATFORM_ADMIN_EMAIL e PLATFORM_ADMIN_PASSWORD. Se estiverem vazias, não faz nada. Se o e-mail
 * já existir, também não faz nada (nunca altera uma conta existente).
 */
@Component
public class PlatformAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PlatformAdminInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String DEFAULT_NAME = "Platform Admin";
    private static final String DEFAULT_PHONE = "00000000000";

    private final AppSecurityProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    public PlatformAdminInitializer(
            AppSecurityProperties properties,
            UserRepository userRepository,
            UserService userService
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppSecurityProperties.PlatformAdmin admin = properties.platformAdmin();

        if (admin == null
                || isBlank(admin.email())
                || isBlank(admin.password())) {
            return;
        }

        if (admin.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "PLATFORM_ADMIN_PASSWORD must have at least "
                            + MIN_PASSWORD_LENGTH + " characters"
            );
        }

        String email = admin.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            return;
        }

        userService.createUser(
                isBlank(admin.name()) ? DEFAULT_NAME : admin.name(),
                email,
                isBlank(admin.phone()) ? DEFAULT_PHONE : admin.phone(),
                admin.password(),
                UserRole.PLATFORM_ADMIN
        );

        LOGGER.info("Platform admin created for {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
