package se.blug.forum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se.blug.forum.model.*;
import se.blug.forum.repo.*;
import java.util.List;
import static se.blug.forum.service.ApiValidation.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ForumService {
    private final ForumRepository forumRepository;
    private final ThreadRepository threadRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"); }
    private void allow(boolean allowed) { if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied"); }
    public boolean admin(User user) { return user != null && !user.isBlocked() && user.getRole() == User.Role.ADMIN; }
    public User user(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id");
        return userRepository.findById(id).orElseThrow(this::missing);
    }
    public Forum forum(Long id) { return forumRepository.findById(id).orElseThrow(this::missing); }
    public ForumThread thread(Long id) { return threadRepository.findById(id).orElseThrow(this::missing); }
    public Post post(Long id) { return postRepository.findById(id).orElseThrow(this::missing); }
    public boolean owner(ForumThread thread, User user) { return user != null && thread.getOwner().getId().equals(user.getId()); }
    private boolean member(java.util.Set<User> users, User user) { return user != null && users.stream().anyMatch(u -> u.getId().equals(user.getId())); }
    public boolean moderator(ForumThread thread, User user) { return owner(thread, user) || member(thread.getModerators(), user); }
    public boolean accessible(ForumThread thread, User user) {
        return !thread.isBlocked() && !thread.getForum().isBlocked()
            && (thread.isPublic() || owner(thread, user) || member(thread.getModerators(), user) || member(thread.getParticipants(), user));
    }
    public void read(ForumThread thread, User user) { if (!accessible(thread, user) && !admin(user)) throw missing(); }
    public void write(ForumThread thread, User user) { read(thread, user); allow(!thread.isBlocked() && !thread.getForum().isBlocked()); }
    public List<Forum> forums() { return forumRepository.findAllByOrderByCreatedAtDesc().stream().filter(f -> !f.isBlocked()).toList(); }
    public List<ForumThread> threads(Long forumId, User user) {
        Forum forum = forum(forumId);
        if (forum.isBlocked()) throw missing();
        // Anonymous lists retain private thread metadata, but never content or relationships.
        return threadRepository.findByForumId(forumId).stream().filter(t -> !t.isBlocked()).toList();
    }
    public ForumThread readThread(Long id, User user) { ForumThread t = thread(id); read(t, user); return t; }
    public List<Post> posts(Long threadId, User user) {
        read(thread(threadId), user);
        return postRepository.findByForumThreadId(threadId).stream().filter(p -> !p.isDeleted() && !p.isBlocked()).toList();
    }
    public Post readPost(Long id, User user) {
        Post p = post(id); read(p.getForumThread(), user);
        if (p.isDeleted() || p.isBlocked()) throw missing();
        return p;
    }
    public Forum createForum(Forum input, User creator) {
        Forum forum = new Forum();
        forum.setName(required(input.getName(), 200, "name"));
        forum.setDescription(optional(input.getDescription(), 2000, "description"));
        forum.setContent(optional(input.getContent(), 20000, "content"));
        forum.setCreatedBy(creator);
        return forumRepository.save(forum);
    }
    public ForumThread createThread(ForumThread input, User owner, Forum forum) {
        allow(!forum.isBlocked());
        ForumThread thread = new ForumThread();
        thread.setTitle(required(input.getTitle(), 200, "title"));
        thread.setContent(optional(input.getContent(), 20000, "content"));
        thread.setPublic(input.isPublic());
        thread.setForum(forum); thread.setOwner(owner);
        return threadRepository.save(thread);
    }
    public ForumThread updateThreadVisibility(Long id, boolean visibility, User user) {
        ForumThread thread = thread(id); write(thread, user); allow(owner(thread, user));
        thread.setPublic(visibility); return thread;
    }
    public ForumThread transferOwnership(Long id, User target, User current) {
        ForumThread thread = thread(id); write(thread, current); allow(owner(thread, current));
        User newOwner = user(target.getId()); allow(!newOwner.isBlocked());
        thread.getParticipants().add(current); thread.setOwner(newOwner); return thread;
    }
    public ForumThread addModerator(Long id, User target, User current) {
        ForumThread thread = thread(id); write(thread, current); allow(owner(thread, current));
        User moderator = user(target.getId()); allow(!moderator.isBlocked());
        thread.getModerators().add(moderator); return thread;
    }
    public ForumThread removeModerator(Long id, User target, User current) {
        ForumThread thread = thread(id); write(thread, current); allow(owner(thread, current));
        User moderator = user(target.getId());
        thread.getModerators().removeIf(u -> u.getId().equals(moderator.getId())); return thread;
    }
    public Post createPost(Post input, User author, ForumThread thread) {
        write(thread, author);
        Post post = new Post(); post.setContent(required(input.getContent(), 20000, "content"));
        post.setForumThread(thread); post.setAuthor(author); return postRepository.save(post);
    }
    public Post updatePost(Long id, String content, User author) {
        Post post = readPost(id, author); write(post.getForumThread(), author);
        allow(post.getAuthor().getId().equals(author.getId()));
        post.setContent(required(content, 20000, "content")); return post;
    }
    public Post deletePost(Long id, User author) {
        Post post = post(id); write(post.getForumThread(), author);
        allow(post.getAuthor().getId().equals(author.getId()));
        post.setDeleted(true); post.setContent(null); return post;
    }
    public void blockPostByModerator(Long id, User current) {
        Post post = post(id); write(post.getForumThread(), current); allow(moderator(post.getForumThread(), current));
        allow(!post.isDeleted()); post.setBlocked(true);
    }
    public User blockUser(Long id) { User user = user(id); user.setBlocked(true); return user; }
    public User restoreUser(Long id) { User user = user(id); user.setBlocked(false); return user; }
    public void deleteForum(Long id) { forumRepository.delete(forum(id)); }
    public void deleteThread(Long id) { threadRepository.delete(thread(id)); }
    public void deletePostAdmin(Long id) { postRepository.delete(post(id)); }
}
