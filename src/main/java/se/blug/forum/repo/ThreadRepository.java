package se.blug.forum.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.blug.forum.model.Thread;
import java.util.List;

@Repository
public interface ThreadRepository extends JpaRepository<Thread, Long> {
    List<Thread> findByForumId(Long forumId);
    List<Thread> findByOwnerId(Long ownerId);
}
