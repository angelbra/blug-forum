package se.blug.forum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.blug.forum.model.*;
import se.blug.forum.repo.*;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ForumService {

    private final ForumRepository forumRepository;
    private final ThreadRepository threadRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // --- Forum Operations ---
    @Transactional
    public Forum createForum(Forum forum, User creator) {
        forum.setCreatedBy(creator);
        return forumRepository.save(forum);
    }

    // --- Thread Operations ---
    @Transactional
    public Thread createThread(Thread thread, User owner, Forum forum) {
        thread.setForum(forum);
        thread.setOwner(owner);
        return threadRepository.save(thread);
    }

    @Transactional
    public Thread updateThreadVisibility(Long threadId, boolean isPublic, User owner) {
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new NoSuchElementException("Thread not found"));
        if (!thread.getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Only the thread owner can change visibility");
        }
        thread.setPublic(isPublic);
        return threadRepository.save(thread);
    }

    @Transactional
    public Thread transferOwnership(Long threadId, User newOwner, User currentOwner) {
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new NoSuchElementException("Thread not found"));
        if (!thread.getOwner().getId().equals(currentOwner.getId())) {
            throw new SecurityException("Only the thread owner can transfer ownership");
        }
        thread.setOwner(newOwner);
        return threadRepository.save(thread);
    }

    @Transactional
    public Thread addModerator(Long threadId, User moderator, User owner) {
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new NoSuchElementException("Thread not found"));
        if (!thread.getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Only the thread owner can add moderators");
        }
        thread.getModerators().add(moderator);
        return threadRepository.save(thread);
    }

    @Transactional
    public Thread removeModerator(Long threadId, User moderator, User owner) {
        Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new NoSuchElementException("Thread not found"));
        if (!thread.getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Only the thread owner can remove moderators");
        }
        thread.getModerators().remove(moderator);
        return threadRepository.save(thread);
    }

    // --- Post Operations ---
    @Transactional
    public Post createPost(Post post, User author, Thread thread) {
        post.setThread(thread);
        post.setAuthor(author);
        return postRepository.save(post);
    }

    @Transactional
    public Post deletePost(Long postId, User author) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        if (!post.getAuthor().getId().equals(author.getId())) {
            throw new SecurityException("Only the author can delete their post");
        }
        post.setDeleted(true);
        return postRepository.save(post);
    }

    // --- Moderator Operations ---
    @Transactional
    public void blockPostByModerator(Long postId, User moderator) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));
        Thread thread = post.getThread();
        if (!thread.getModerators().contains(moderator) && !thread.getOwner().getId().equals(moderator.getId())) {
            throw new SecurityException("Only moderators or the thread owner can block content");
        }
        post.setDeleted(true);
        postRepository.save(post);
    }

    // --- Admin Operations ---
    @Transactional
    public User blockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.setBlocked(true);
        return userRepository.save(user);
    }

    @Transactional
    public User restoreUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        user.setBlocked(false);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteForum(Long forumId) {
        forumRepository.deleteById(forumId);
    }

    @Transactional
    public void deleteThread(Long threadId) {
        threadRepository.deleteById(threadId);
    }

    @Transactional
    public void deletePostAdmin(Long postId) {
        postRepository.deleteById(postId);
    }
}
