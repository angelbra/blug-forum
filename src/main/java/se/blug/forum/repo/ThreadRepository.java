package se.blug.forum.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import se.blug.forum.model.ForumThread;
import java.util.List;

@Repository
public interface ThreadRepository extends JpaRepository<ForumThread, Long> {
    List<ForumThread> findByForumId(Long forumId);
    List<ForumThread> findByOwnerId(Long ownerId);
}
