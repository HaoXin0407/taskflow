package com.taskflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.common.BusinessException;
import com.taskflow.common.Constants;
import com.taskflow.common.ResultCode;
import com.taskflow.dto.LoginDTO;
import com.taskflow.dto.RegisterDTO;
import com.taskflow.entity.SysPermission;
import com.taskflow.entity.SysUser;
import com.taskflow.entity.SysUserRole;
import com.taskflow.mapper.SysNoticeMapper;
import com.taskflow.mapper.SysPermissionMapper;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.mapper.SysUserRoleMapper;
import com.taskflow.security.LoginUser;
import com.taskflow.util.JwtUtils;
import com.taskflow.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务：登录、注册、加载用户上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysNoticeMapper noticeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /** 普通员工角色ID，注册时默认绑定 */
    private static final Long DEFAULT_ROLE_ID = 3L;

    /**
     * 登录。
     *
     * <p>安全要点（面试可讲）：</p>
     * <ol>
     *     <li>用 BCrypt 比对密码，而不是明文 equals</li>
     *     <li>「用户不存在」与「密码错误」返回同一句提示，避免账号枚举攻击</li>
     *     <li>只有登录成功才更新最后登录时间</li>
     * </ol>
     */
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername())
                .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 更新最后登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        log.info("用户登录成功: {} ({})", user.getUsername(), user.getNickname());

        return buildLoginVO(user, jwtUtils.generateToken(user.getId(), user.getUsername()));
    }

    /**
     * 注册。新注册用户默认绑定「普通员工」角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        BusinessException.throwIf(exists != null && exists > 0, ResultCode.USERNAME_EXISTS);

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setDept(dto.getDept());
        user.setStatus(1);
        user.setRemark("注册新用户");
        userMapper.insert(user);

        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(ur);

        log.info("新用户注册成功: {}", dto.getUsername());
    }

    /**
     * 供 JWT 过滤器调用：根据用户ID加载最新的登录上下文。
     * <p>每次请求都重新查库，保证「管理员改了权限，被改用户下一次请求立即生效」，
     * 这是很多初级项目会漏掉的细节。</p>
     */
    public LoginUser loadLoginUserById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            return null;
        }
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(userId);
        List<String> permCodes = userMapper.selectPermCodesByUserId(userId);
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDept(),
                new HashSet<>(permCodes),
                new HashSet<>(roleCodes)
        );
    }

    /**
     * 组装登录返回对象（令牌 + 用户信息 + 权限 + 菜单）。
     */
    public LoginVO buildLoginVO(SysUser user, String token) {
        Set<String> roles = new HashSet<>(userMapper.selectRoleCodesByUserId(user.getId()));
        List<String> permCodes = userMapper.selectPermCodesByUserId(user.getId());

        Set<String> permissions = new HashSet<>();
        if (roles.contains(Constants.ROLE_ADMIN)) {
            // 超级管理员拥有全部权限
            permissionMapper.selectAllMenus()
                    .forEach(p -> {
                        if (p.getPermCode() != null) {
                            permissions.add(p.getPermCode());
                        }
                    });
        } else {
            permissions.addAll(permCodes);
        }

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setTokenPrefix(jwtUtils.getPrefix());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setDept(user.getDept());
        vo.setAvatar(user.getAvatar());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        vo.setMenus(permissionMapper.selectMenuByUserId(user.getId()));
        vo.setUnreadCount(noticeMapper.countUnread(user.getId()));
        return vo;
    }
}