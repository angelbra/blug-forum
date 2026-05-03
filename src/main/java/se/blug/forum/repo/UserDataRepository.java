package se.blug.forum.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import se.blug.forum.model.UserData;

import java.util.Optional;

public interface UserDataRepository extends JpaRepository<UserData, Long> {
    Optional<UserData> findByUsername(String username);
    UserData findByEmail(String email);

}
