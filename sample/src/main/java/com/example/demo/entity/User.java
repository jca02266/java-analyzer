package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // ステータス定数（プレフィックスクラスタ検出対象）
    public static final int STATUS_ACTIVE   = 1;
    public static final int STATUS_INACTIVE = 2;
    public static final int STATUS_DELETED  = 9;

    // ロール定数
    public static final String ROLE_ADMIN  = "ADMIN";
    public static final String ROLE_USER   = "USER";
    public static final String ROLE_GUEST  = "GUEST";

    private Long   id;
    private String username;
    private String email;
    private String passwordHash;
    private int    status;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
