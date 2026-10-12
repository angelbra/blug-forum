package se.blug.forum.api;

import java.time.LocalDateTime;
import se.blug.forum.model.*;

/** Explicit API data: no password hashes or JPA relationship graphs. */
public final class ApiData {
    private ApiData() {}
    public record Registration(String username, String email, String password) {}
    public record ProfileUpdate(String email, String password) {}
    public record ForumInput(String name, String description, String content) {}
    public record ThreadInput(String title, String content, Boolean isPublic) {}
    public record PostInput(String content) {}
    public record Visibility(Boolean isPublic) {}
    public record UserReference(Long id) {}
    public record ForumView(Long id, String name, String description, LocalDateTime createdAt, boolean blocked) {
        public static ForumView of(Forum f) { return new ForumView(f.getId(), f.getName(), f.getDescription(), f.getCreatedAt(), f.isBlocked()); }
    }
    public record ThreadSummary(Long id, String title, boolean isPublic, LocalDateTime createdAt) {
        public static ThreadSummary of(ForumThread t) { return new ThreadSummary(t.getId(), t.getTitle(), t.isPublic(), t.getCreatedAt()); }
    }
    public record ThreadView(Long id, Long forumId, String title, String content, Long ownerId,
                             boolean isPublic, LocalDateTime createdAt, boolean blocked) {
        public static ThreadView of(ForumThread t) { return new ThreadView(t.getId(), t.getForum().getId(), t.getTitle(), t.getContent(), t.getOwner().getId(), t.isPublic(), t.getCreatedAt(), t.isBlocked()); }
    }
    public record PostView(Long id, Long threadId, String content, Long authorId, LocalDateTime createdAt, LocalDateTime updatedAt, boolean blocked) {
        public static PostView of(Post p) { return new PostView(p.getId(), p.getForumThread().getId(), p.getContent(), p.getAuthor().getId(), p.getCreatedAt(), p.getUpdatedAt(), p.isBlocked()); }
    }
    public record Profile(Long id, String username, String email, String role, boolean blocked) {
        public static Profile of(User u) { return new Profile(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name(), u.isBlocked()); }
    }
}
