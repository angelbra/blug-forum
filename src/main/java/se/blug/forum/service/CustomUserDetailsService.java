package se.blug.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import se.blug.forum.model.UserData;
import se.blug.forum.repo.UserDataRepository;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDataRepository userDataRepository;

    @Override
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        Optional<UserData> userModel = userDataRepository.findByUsername(username);

        if (userModel.isEmpty()) {
            log.warn("Username {} not found", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return User.builder()
                .username(userModel.get().getUsername())
                .password(userModel.get().getPassword())
                .roles(userModel.get().getRole())
                .build();
    }
}
