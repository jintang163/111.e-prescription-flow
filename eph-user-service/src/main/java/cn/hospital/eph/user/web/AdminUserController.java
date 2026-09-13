package cn.hospital.eph.user.web;

import cn.hospital.eph.common.enums.UserType;
import cn.hospital.eph.common.security.RequireRole;
import cn.hospital.eph.common.web.Result;
import cn.hospital.eph.user.ca.CaService;
import cn.hospital.eph.user.entity.SysUser;
import cn.hospital.eph.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequireRole("ADMIN")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper userMapper;
    private final CaService caService;

    @GetMapping("/users")
    public Result<Page<SysUser>> users(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) Integer userType) {
        Page<SysUser> p = new Page<>(page, size);
        if (userType != null) {
            return Result.ok(userMapper.selectPage(p,
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                            .eq("user_type", userType).orderByDesc("id")));
        }
        return Result.ok(userMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>().orderByDesc("id")));
    }

    /** 为医生/药师签发（或轮换）签名证书 */
    @PostMapping("/users/{id}/certificates")
    public Result<Map<String, String>> issueCertificate(@PathVariable long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return Result.fail(40400, "用户不存在");
        }
        UserType type = UserType.ofCode(user.getUserType());
        String ou = switch (type) {
            case DOCTOR -> "DOCTOR";
            case PHARMACIST -> "PHARMACIST";
            default -> throw new IllegalArgumentException("仅医生/药师需要签名证书");
        };
        String serial = caService.issueUserCertificate(user.getId(), user.getRealName(), ou);
        return Result.ok(Map.of("certSerial", serial));
    }

    @PostMapping("/keys/rotate")
    public Result<Map<String, String>> rotate(@RequestParam long userId) {
        SysUser user = userMapper.selectById(userId);
        UserType type = UserType.ofCode(user.getUserType());
        String serial = caService.issueUserCertificate(user.getId(), user.getRealName(), type.name());
        return Result.ok(Map.of("certSerial", serial));
    }
}
