package cn.hospital.eph.user.service;

import cn.hospital.eph.common.enums.UserType;
import cn.hospital.eph.common.security.JwtService;
import cn.hospital.eph.common.security.SignGrantService;
import cn.hospital.eph.common.web.BizException;
import cn.hospital.eph.common.web.ErrorCode;
import cn.hospital.eph.user.entity.SysUser;
import cn.hospital.eph.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final SignGrantService signGrantService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public record LoginResult(String accessToken, long expiresIn, SysUser user) {
    }

    public LoginResult login(String username, String rawPassword) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        String role = UserType.ofCode(user.getUserType()).name();
        String token = jwtService.issueAccessToken(user.getId(), user.getUsername(), user.getRealName(), role);
        return new LoginResult(token, jwtService.getAccessTtlSeconds(), user);
    }

    /** 签名前重认证（密码），通过后发放一次性 5 分钟签名许可 */
    public void signGrant(long userId, String rawPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "密码错误");
        }
        signGrantService.grant(userId);
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }
}
