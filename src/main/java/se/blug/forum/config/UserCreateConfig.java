package se.blug.forum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import se.blug.forum.model.User;
import se.blug.forum.repo.UserRepository;
import se.blug.forum.service.ApiValidation;

/** Optional first-run provisioning into the same users table used by authentication. */
@Service
public class UserCreateConfig implements CommandLineRunner {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final Environment environment;
    public UserCreateConfig(PasswordEncoder passwordEncoder, UserRepository userRepository, Environment environment) {
        this.passwordEncoder = passwordEncoder; this.userRepository = userRepository; this.environment = environment;
    }
    @Override
    public void run(String... args) {
        String password = environment.getProperty("blug.admin.password");
        if (password == null || password.isBlank()) return;
        String username = environment.getProperty("blug.admin.username", "admin");
        if (userRepository.existsByUsername(username)) return;
        User admin = new User();
        admin.setUsername(ApiValidation.required(username, 50, "username"));
        admin.setPassword(passwordEncoder.encode(ApiValidation.password(password)));
        admin.setEmail(ApiValidation.email(environment.getProperty("blug.admin.email")));
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);
    }
}
