package se.blug.forum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import se.blug.forum.model.User;
import se.blug.forum.repo.UserRepository;

@Service
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository users;
    public User optional(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String) return null;
        User user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!(authentication.getPrincipal() instanceof SessionUser principal) || !principal.getId().equals(user.getId()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (user.isBlocked()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account blocked");
        return user;
    }
    public User require(Authentication authentication) {
        User user = optional(authentication);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user;
    }
}
