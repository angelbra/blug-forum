package se.blug.forum.config;


import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import se.blug.forum.model.UserData;
import se.blug.forum.repo.UserDataRepository;

@Service
public class UserCreateConfig implements CommandLineRunner {

    private final UserDataRepository userDataRepository;

    private final PasswordEncoder passwordEncoder;

    public UserCreateConfig(UserDataRepository userDataRepository, PasswordEncoder passwordEncoder) {
        this.userDataRepository = userDataRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        UserData entity = new UserData();
        entity.setId(1L);
        entity.setUsername("admin");
        entity.setPassword(passwordEncoder.encode("admin"));
        entity.setEmail("");
        entity.setRole("ADMIN");
        userDataRepository.save(entity);

    }
}
