package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<User> findActiveUsers() {
        return userMapper.findAll().stream()
                .filter(u -> u.getStatus() == User.STATUS_ACTIVE)
                .sorted((a, b) -> a.getUsername().compareTo(b.getUsername()))
                .collect(Collectors.toList());
    }

    public Optional<User> findById(Long id) {
        return userMapper.findById(id);
    }

    /**
     * ユーザー登録。メールアドレス重複チェック付き。
     * ネスト深度・連続代入ブロック検出対象。
     */
    @Transactional
    public User register(String username, String email, String password, String role) {
        // メールアドレス重複チェック
        if (userMapper.countByEmail(email) > 0) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        // バリデーション（ネスト深度の例）
        if (username != null && !username.isEmpty()) {
            if (username.length() < 3) {
                throw new IllegalArgumentException("Username too short");
            }
            if (username.length() > 50) {
                throw new IllegalArgumentException("Username too long");
            }
            for (char c : username.toCharArray()) {
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    throw new IllegalArgumentException("Invalid character in username: " + c);
                }
            }
        } else {
            throw new IllegalArgumentException("Username is required");
        }

        // 連続代入ブロック（5 行以上）
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hashPassword(password));
        user.setStatus(User.STATUS_ACTIVE);
        user.setRole(role != null ? role : User.ROLE_USER);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        return user;
    }

    @Transactional
    public void deactivate(Long id) {
        User user = userMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setStatus(User.STATUS_INACTIVE);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = userMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setStatus(User.STATUS_DELETED);
        user.setDeletedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    public List<User> search(String keyword, Integer status, String role) {
        int resolvedStatus = (status != null) ? status : User.STATUS_ACTIVE;
        String resolvedRole = (role != null) ? role : "";
        return userMapper.search(keyword, resolvedStatus, resolvedRole);
    }

    // デッドコード候補（private で未呼び出し）
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    private String hashPassword(String password) {
        // 実際は BCrypt 等を使う
        return Integer.toHexString(password.hashCode());
    }
}
