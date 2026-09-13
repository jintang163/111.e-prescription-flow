package cn.hospital.eph.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 从 JWT 解析出的当前登录用户 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {
    private long userId;
    private String username;
    private String realName;
    private String role;
}
