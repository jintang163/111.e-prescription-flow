package cn.hospital.eph.user.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class AuthDtos {
    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class SignGrantRequest {
        @NotBlank
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String accessToken;
        private long expiresIn;
        private Long userId;
        private String username;
        private String realName;
        private String role;
    }
}
