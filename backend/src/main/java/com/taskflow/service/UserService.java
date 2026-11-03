package com.taskflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskflow.common.BusinessException;
import com.taskflow.common.PageResult;
import com.taskflow.common.ResultCode;
import com.taskflow.dto.PasswordDTO;
import com.taskflow.dto.UserDTO;
import com.taskflow.entity.SysRole;
import com.taskflow.entity.SysUser;
import com.taskflow.entity.SysUserRole;
import com.taskflow.mapper.SysRoleMapper;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.mapper.SysUserRoleMapper;
import com.taskflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户。
     */
    public PageResult<SysUser> page(Long pageNum, Long pageSize, String keyword, Integer status) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);

        IPage<SysUser> result = userMapper.selectPage(page, Wrappers.<SysUser>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(SysUser::getUsername, keyword).or()
                        .like(SysUser::getNickname, keyword).or()
                        .like(SysUser::getEmail, keyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId));

        // 安全：绝不把密码返回给前端
        result.getRecords().forEach(u -> u.setPassword(null));

        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 查询用户详情（含角色ID）。
     */
    public SysUser detail(Long id) {
        SysUser user = userMapper.selectById(id);
        BusinessException.throwIf(user == null, ResultCode.USER_NOT_FOUND);
        user.setPassword(null);
        return user;
    }

    /**
     * 查询用户已分配的角色ID。
     */
    public List<Long> roleIds(Long userId) {
        return userMapper.selectRoleIdsByUserId(userId);
    }

    /**
     * 新增用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(UserDTO dto) {
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        BusinessException.throwIf(exists != null && exists > 0, ResultCode.USERNAME_EXISTS);
        BusinessException.throwIf(dto.getPassword() == null || dto.getPassword().length() < 6,
                "密码长度不能少于 6 位");

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setDept(dto.getDept());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setRemark(dto.getRemark());
        userMapper.insert(user);

        // 分配角色
        if (dto.getRoleIds() != null) {
            dto.getRoleIds().forEach(roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            });
        }
    }

    /**
     * 修改用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(UserDTO dto) {
        BusinessException.throwIf(dto.getId() == null, "用户ID不能为空");
        SysUser exist = userMapper.selectById(dto.getId());
        BusinessException.throwIf(exist == null, ResultCode.USER_NOT_FOUND);

        SysUser user = new SysUser();
        user.setId(dto.getId());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setDept(dto.getDept());
        user.setStatus(dto.getStatus());
        user.setRemark(dto.getRemark());
        // 密码留空则不修改
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(user);

        // 重新分配角色：先删后插
        if (dto.getRoleIds() != null) {
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                    .eq(SysUserRole::getUserId, dto.getId()));
            dto.getRoleIds().forEach(roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(dto.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            });
        }
    }

    /**
     * 删除用户（逻辑删除）。不能删除自己。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BusinessException.throwIf(id.equals(SecurityUtils.getUserId()), "不能删除当前登录账号");
        userMapper.deleteById(id);

        // 同时清理角色关联
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, id));
    }

    /**
     * 重置他人密码（管理员功能）。
     */
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    /**
     * 修改自己的密码。
     */
    public void changePassword(PasswordDTO dto) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userMapper.selectById(userId);
        BusinessException.throwIf(user == null, ResultCode.USER_NOT_FOUND);

        // 必须校验原密码，防止会话被劫持后直接改密
        BusinessException.throwIf(!passwordEncoder.matches(dto.getOldPassword(), user.getPassword()),
                ResultCode.OLD_PASSWORD_ERROR);

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);

        log.info("用户修改密码: {}", user.getUsername());
    }

    /**
     * 获取当前登录用户的基本信息。
     */
    public SysUser currentUser() {
        SysUser user = userMapper.selectById(SecurityUtils.getUserId());
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    /**
     * 查询所有启用用户（下拉选择框用，如指派处理人、选择审批人）。
     */
    public List<SysUser> listAllEnabled() {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    /**
     * 查询所有角色。
     */
    public List<SysRole> listRoles() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
    }
}