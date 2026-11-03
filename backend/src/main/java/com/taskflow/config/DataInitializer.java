package com.taskflow.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.entity.SysUser;
import com.taskflow.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动数据初始化。
 *
 * <p>作用：sql 脚本里的测试用户密码是明文 123456（方便你直接看脚本明白），
 * 应用首次启动时自动把它们升级为 BCrypt 密文。这样你既拿到了可读的初始化脚本，
 * 又得到了安全的密码存储。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getDeleted, 0));

        int upgraded = 0;
        for (SysUser user : users) {
            String pwd = user.getPassword();
            // BCrypt 密文固定以 $2a$ / $2b$ / $2y$ 开头；否则视为明文，需升级
            if (pwd != null && !pwd.startsWith("$2")) {
                SysUser update = new SysUser();
                update.setId(user.getId());
                update.setPassword(passwordEncoder.encode(pwd));
                userMapper.updateById(update);
                upgraded++;
            }
        }

        if (upgraded > 0) {
            log.info("已将 {} 个用户的明文密码升级为 BCrypt 密文", upgraded);
        }

        log.info("""

                ------------------------------------------------------------------
                  初始化完成！可用账号（密码均为 123456）：
                    admin    - 系统管理员（全部权限）
                    manager  - 部门主管（工单受理 + 审批）
                    zhangsan - 普通员工（仅创建与查看）
                ------------------------------------------------------------------
                """);
    }
}