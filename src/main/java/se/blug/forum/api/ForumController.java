package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import se.blug.forum.model.*;
import se.blug.forum.repo.*;
import se.blug.forum.service.ForumService;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;
    private final ForumRepository forumRepository;
    private final ThreadRepository threadRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(User.Role.USER);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
    }

    @PostMapping("/forums")
    public ResponseEntity<Forum> createForum(@RequestBody Forum forum, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Forum createdForum = forumService.createForum(forum, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdForum);
    }

    @PostMapping("/forums/{forumId}/threads")
    public ResponseEntity<Thread> createThread(@PathVariable Long forumId, @RequestBody Thread thread, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Forum forum = forumRepository.findById(forumId)
                .orElseThrow(() -> new RuntimeException("Forum not found"));
        Thread createdThread = forumService.createThread(thread, currentUser, forum);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdThread);
    }

    @PostMapping("/threads/{threadId}/posts")
    public ResponseEntity<Post> createPost(@PathVariable Long threadId, @RequestBody Post post, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
        Post createdPost = forumService.createPost(post, currentUser, thread);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    // --- Thread Ownership & Visibility ---
    @PutMapping("/threads/{threadId}/visibility")
    public ResponseEntity<Thread> updateThreadVisibility(@PathVariable Long threadId, @RequestBody Thread visibilityUpdate, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread updatedThread = forumService.updateThreadVisibility(threadId, visibilityUpdate.isPublic(), currentUser);
        return ResponseEntity.ok(updatedThread);
    }

    @PostMapping("/threads/{threadId}/transfer-ownership")
    public ResponseEntity<Thread> transferOwnership(@PathVariable Long threadId, @RequestBody User newOwner, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread transferredThread = forumService.transferOwnership(threadId, newOwner, currentUser);
        return ResponseEntity.ok(transferredThread);
    }

    // --- Moderator Management ---
    @PostMapping("/threads/{threadId}/moderators/add")
    public ResponseEntity<Thread> addModerator(@PathVariable Long threadId, @RequestBody User moderator, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread updatedThread = forumService.addModerator(threadId, moderator, currentUser);
        return ResponseEntity.ok(updatedThread);
    }

    @PostMapping("/threads/{threadId}/moderators/remove")
    public ResponseEntity<Thread> removeModerator(@PathVariable Long threadId, @RequestBody User moderator, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Thread updatedThread = forumService.removeModerator(threadId, moderator, currentUser);
        return ResponseEntity.ok(updatedThread);
    }

    // --- Post Management ---
    @PutMapping("/posts/{postId}")
    public ResponseEntity<Post> updatePost(@PathVariable Long postId, @RequestBody Post postUpdate, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Post existingPost = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!existingPost.getAuthor().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        existingPost.setContent(postUpdate.getContent());
        Post updatedPost = postRepository.save(existingPost);
        return ResponseEntity.ok(updatedPost);
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Long postId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        forumService.deletePost(postId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/block")
    public ResponseEntity<Void> blockPostByModerator(@PathVariable Long postId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        forumService.blockPostByModerator(postId, currentUser);
        return ResponseEntity.noContent().build();
    }

    // --- Admin Endpoints ---
    @PostMapping("/admin/users/{userId}/block")
    public ResponseEntity<User> blockUser(@PathVariable Long userId) {
        User blockedUser = forumService.blockUser(userId);
        return ResponseEntity.ok(blockedUser);
    }

    @PostMapping("/admin/users/{userId}/restore")
    public ResponseEntity<User> restoreUser(@PathVariable Long userId) {
        User restoredUser = forumService.restoreUser(userId);
        return ResponseEntity.ok(restoredUser);
    }

    @DeleteMapping("/admin/forums/{forumId}")
    public ResponseEntity<Void> deleteForum(@PathVariable Long forumId) {
        forumService.deleteForum(forumId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/threads/{threadId}")
    public ResponseEntity<Void> deleteThread(@PathVariable Long threadId) {
        forumService.deleteThread(threadId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/posts/{postId}")
    public ResponseEntity<Void> deletePostAdmin(@PathVariable Long postId) {
        forumService.deletePostAdmin(postId);
        return ResponseEntity.noContent().build();
    }
}
