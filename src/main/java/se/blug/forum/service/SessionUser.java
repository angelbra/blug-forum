package se.blug.forum.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

/** The persistent id prevents an old session from becoming a newly registered namesake. */
public class SessionUser extends org.springframework.security.core.userdetails.User {
    private final Long id;
    public SessionUser(se.blug.forum.model.User user) {
        super(user.getUsername(), user.getPassword(), List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.id = user.getId();
    }
    public Long getId() { return id; }
}
