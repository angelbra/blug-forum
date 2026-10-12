package se.blug.forum.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import se.blug.forum.model.*;
import se.blug.forum.repo.UserRepository;
import se.blug.forum.service.*;
import java.util.List;
import java.util.Map;
import static se.blug.forum.api.ApiData.*;
import static se.blug.forum.service.ApiValidation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Transactional
public class ForumController {
    private final ForumService service;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @GetMapping("/forums")
    public List<ForumView> forums() { return service.forums().stream().map(ForumView::of).toList(); }
    @GetMapping("/forums/{id}/threads")
    public List<ThreadSummary> threads(@PathVariable Long id, Authentication auth) {
        return service.threads(id, currentUser.optional(auth)).stream().map(ThreadSummary::of).toList();
    }
    @GetMapping("/threads/{id}")
    public ThreadView thread(@PathVariable Long id, Authentication auth) { return ThreadView.of(service.readThread(id, currentUser.optional(auth))); }
    @GetMapping("/threads/{id}/posts")
    public List<PostView> posts(@PathVariable Long id, Authentication auth) {
        return service.posts(id, currentUser.optional(auth)).stream().map(PostView::of).toList();
    }
    @GetMapping("/posts/{id}")
    public PostView post(@PathVariable Long id, Authentication auth) { return PostView.of(service.readPost(id, currentUser.optional(auth))); }
    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@RequestBody Registration request) {
        String username = required(request.username(), 50, "username");
        if (!username.matches("[A-Za-z0-9_.-]+")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username");
        String email = email(request.email()); password(request.password());
        if (users.existsByUsername(username) || users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email unavailable");
        User user = new User(); user.setUsername(username); user.setEmail(email);
        user.setPassword(passwords.encode(request.password())); user.setRole(User.Role.USER); users.save(user);
        return Map.of("message", "User registered successfully");
    }
    @PostMapping("/forums")
    @ResponseStatus(HttpStatus.CREATED)
    public ForumView createForum(@RequestBody ForumInput request, Authentication auth) {
        Forum input = new Forum(); input.setName(request.name()); input.setDescription(request.description()); input.setContent(request.content());
        return ForumView.of(service.createForum(input, currentUser.require(auth)));
    }
    @PostMapping("/forums/{forumId}/threads")
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadView createThread(@PathVariable Long forumId, @RequestBody ThreadInput request, Authentication auth) {
        ForumThread input = new ForumThread(); input.setTitle(request.title()); input.setContent(request.content());
        input.setPublic(request.isPublic() == null || request.isPublic());
        return ThreadView.of(service.createThread(input, currentUser.require(auth), service.forum(forumId)));
    }
    @PostMapping("/threads/{threadId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostView createPost(@PathVariable Long threadId, @RequestBody PostInput request, Authentication auth) {
        Post input = new Post(); input.setContent(request.content());
        return PostView.of(service.createPost(input, currentUser.require(auth), service.thread(threadId)));
    }
    @PutMapping("/threads/{threadId}/visibility")
    public ThreadView visibility(@PathVariable Long threadId, @RequestBody Visibility request, Authentication auth) {
        if (request.isPublic() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "isPublic required");
        return ThreadView.of(service.updateThreadVisibility(threadId, request.isPublic(), currentUser.require(auth)));
    }
    private User reference(UserReference request) { return service.user(request.id()); }
    @PostMapping("/threads/{threadId}/transfer-ownership")
    public ThreadView transfer(@PathVariable Long threadId, @RequestBody UserReference request, Authentication auth) {
        return ThreadView.of(service.transferOwnership(threadId, reference(request), currentUser.require(auth)));
    }
    @PostMapping("/threads/{threadId}/moderators/add")
    public ThreadView addModerator(@PathVariable Long threadId, @RequestBody UserReference request, Authentication auth) {
        return ThreadView.of(service.addModerator(threadId, reference(request), currentUser.require(auth)));
    }
    @PostMapping("/threads/{threadId}/moderators/remove")
    public ThreadView removeModerator(@PathVariable Long threadId, @RequestBody UserReference request, Authentication auth) {
        return ThreadView.of(service.removeModerator(threadId, reference(request), currentUser.require(auth)));
    }
    @PutMapping("/posts/{postId}")
    public PostView updatePost(@PathVariable Long postId, @RequestBody PostInput request, Authentication auth) {
        return PostView.of(service.updatePost(postId, request.content(), currentUser.require(auth)));
    }
    @PostMapping("/threads/{threadId}/participants/add")
    public ThreadView addParticipant(@PathVariable Long threadId, @RequestBody UserReference request, Authentication auth) {
        return ThreadView.of(service.addParticipant(threadId, reference(request), currentUser.require(auth)));
    }
    @PostMapping("/threads/{threadId}/participants/remove")
    public ThreadView removeParticipant(@PathVariable Long threadId, @RequestBody UserReference request, Authentication auth) {
        return ThreadView.of(service.removeParticipant(threadId, reference(request), currentUser.require(auth)));
    }
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long postId, Authentication auth) { service.deletePost(postId, currentUser.require(auth)); }
    @PostMapping("/posts/{postId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockPost(@PathVariable Long postId, Authentication auth) { service.blockPostByModerator(postId, currentUser.require(auth)); }
    private void admin(Authentication auth) {
        if (!service.admin(currentUser.require(auth))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission denied");
    }
    @PutMapping("/threads/{id}")
    public ThreadView updateThread(@PathVariable Long id, @RequestBody ThreadInput request, Authentication auth) {
        return ThreadView.of(service.updateOwnedThread(id, request.title(), request.content(), currentUser.require(auth)));
    }
    @DeleteMapping("/threads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnedThread(@PathVariable Long id, Authentication auth) { service.deleteOwnedThread(id, currentUser.require(auth)); }
    @GetMapping("/users/me")
    public Profile profile(Authentication auth) { return Profile.of(currentUser.require(auth)); }
    @PutMapping("/users/me")
    public Profile updateProfile(@RequestBody ProfileUpdate request, Authentication auth, jakarta.servlet.http.HttpServletRequest servletRequest) {
        User user = currentUser.require(auth);
        if (request.email() == null && request.password() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile fields required");
        if (request.email() != null) {
            String email = email(request.email());
            if (!email.equals(user.getEmail()) && users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email unavailable");
            user.setEmail(email);
        }
        if (request.password() != null) {
            user.setPassword(passwords.encode(password(request.password())));
            servletRequest.getSession().invalidate();
        }
        return Profile.of(user);
    }
    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(Authentication auth, jakarta.servlet.http.HttpServletRequest request) {
        service.deleteUser(currentUser.require(auth).getId()); request.getSession().invalidate();
    }
    @PostMapping("/admin/users/{id}/block")
    public Profile blockUser(@PathVariable Long id, Authentication auth) { admin(auth); return Profile.of(service.blockUser(id)); }
    @PostMapping("/admin/users/{id}/restore")
    public Profile restoreUser(@PathVariable Long id, Authentication auth) { admin(auth); return Profile.of(service.restoreUser(id)); }
    @DeleteMapping("/admin/forums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForum(@PathVariable Long id, Authentication auth) { admin(auth); service.deleteForum(id); }
    @DeleteMapping("/admin/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, Authentication auth) { admin(auth); service.deleteUser(id); }
    @PostMapping("/admin/forums/{id}/block")
    public ForumView blockForum(@PathVariable Long id, Authentication auth) { admin(auth); return ForumView.of(service.blockForum(id, true)); }
    @PostMapping("/admin/forums/{id}/restore")
    public ForumView restoreForum(@PathVariable Long id, Authentication auth) { admin(auth); return ForumView.of(service.blockForum(id, false)); }
    @PostMapping("/admin/threads/{id}/block")
    public ThreadView blockThread(@PathVariable Long id, Authentication auth) { admin(auth); return ThreadView.of(service.blockThread(id, true)); }
    @PostMapping("/admin/threads/{id}/restore")
    public ThreadView restoreThread(@PathVariable Long id, Authentication auth) { admin(auth); return ThreadView.of(service.blockThread(id, false)); }
    @PostMapping("/admin/posts/{id}/block")
    public PostView blockPostAdmin(@PathVariable Long id, Authentication auth) { admin(auth); return PostView.of(service.blockPostAdmin(id, true)); }
    @PostMapping("/admin/posts/{id}/restore")
    public PostView restorePost(@PathVariable Long id, Authentication auth) { admin(auth); return PostView.of(service.blockPostAdmin(id, false)); }
    @DeleteMapping("/admin/threads/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteThread(@PathVariable Long id, Authentication auth) { admin(auth); service.deleteThread(id); }
    @DeleteMapping("/admin/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePostAdmin(@PathVariable Long id, Authentication auth) { admin(auth); service.deletePostAdmin(id); }
}
