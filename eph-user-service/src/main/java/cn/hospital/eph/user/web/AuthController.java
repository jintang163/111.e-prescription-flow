package cn.hospital.eph.user.web;

import cn.hospital.eph.common.enums.UserType;
import cn.hospital.eph.common.security.LoginUser;
import cn.hospital.eph.common.security.UserContext;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        AuthService.LoginResult r = authService.login(req.getUsername(), req.getPassword());
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        resp.setAccessToken(r.accessToken());
        resp.setExpiresIn(r.expiresIn());
        resp.setUserId(r.user().getId());
        resp.setUsername(r.user().getUsername());
        resp.setRealName(r.user().getRealName());
        resp.setRole(UserType.ofCode(r.user().getUserType()).name());
        return Result.ok(resp);
    }

    @PostMapping("/sign-grant")
    public Result<Void> signGrant(@Valid @RequestBody AuthDtos.SignGrantRequest req) {
        LoginUser me = UserContext.get();
        authService.signGrant(me.getUserId(), req.getPassword());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        LoginUser u = UserContext.get();
        return Result.ok(Map.of(
                "userId", u.getUserId(),
                "username", u.getUsername(),
                "realName", u.getRealName(),
                "role", u.getRole()));
    }
}
