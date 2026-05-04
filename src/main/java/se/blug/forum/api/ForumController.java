package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import se.blug.forum.model.*;
import se.blug.forum.repo.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ForumController {

    private final ForumRepository forumRepository;
    private final ThreadRepository threadRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @GetMapping("/forums")
    public ResponseEntity<List<Forum>> getAllForums() {
        return ResponseEntity.ok(forumRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/forums/{id}/threads")
    public ResponseEntity<List<Thread>> getThreadsByForum(@PathVariable Long id) {
        List<Thread> threads = threadRepository.findByForumId(id);
        return ResponseEntity.ok(threads);
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<Thread> getThread(@PathVariable Long id) {
        Optional<Thread> thread = threadRepository.findById(id);
        return thread.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/auth/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return ResponseEntity.badRequest().body("Username already taken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body("Email already taken");
        }
        user.setPassword(new BCryptPasswordEncoder().encode(user.getPassword()));
        user.setRole(User.Role.USER);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    @PostMapping("/forums")
    public ResponseEntity<Forum> createForum(@RequestBody Forum forum, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        forum.setCreatedBy(currentUser);
        forumRepository.save(forum);
        return ResponseEntity.status(HttpStatus.CREATED).body(forum);
    }

    @PostMapping("/forums/{forumId}/threads")
    public ResponseEntity<Thread> createThread(@PathVariable Long forumId, @RequestBody Thread thread, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Forum forum = forumRepository.findById(forumId)
                .orElseThrow(() -> new RuntimeException("Forum not found"));
        thread.setForum(forum);
        thread.setOwner(currentUser);
        threadRepository.save(thread);
        return ResponseEntity.status(HttpStatus.CREATED).body(thread);
    }

    @PostMapping("/threads/{threadId}/posts")
    public ResponseEntity<Post> createPost(@PathVariable Long threadId, @RequestBody Post post, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
        post.setThread(thread);
        post.setAuthor(currentUser);
        postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }
}
