package com.taskflow.service;

import com.taskflow.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码安全相关的单元测试。
 *
 * <p>展示如何测试密码加密逻辑 —— 这是面试官最容易追问「你怎么保证用户密码安全」的地方，
 * 有测试代码比口头解释有说服力得多。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("密码安全测试")
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("BCrypt 加密后的密码不等于明文")
    void passwordShouldNotEqualPlainText() {
        String raw = "123456";
        String encoded = passwordEncoder.encode(raw);

        assertNotEquals(raw, encoded, "加密后的密码不能等于明文");
        assertTrue(encoded.startsWith("$2"), "BCrypt 密文应以 $2 开头");
    }

    @Test
    @DisplayName("BCrypt 自带随机盐，同一密码两次加密结果不同但都能验证通过")
    void bcryptShouldUseRandomSalt() {
        String raw = "123456";
        String encoded1 = passwordEncoder.encode(raw);
        String encoded2 = passwordEncoder.encode(raw);

        assertNotEquals(encoded1, encoded2, "BCrypt 应使用随机盐，两次结果应不同");
        assertTrue(passwordEncoder.matches(raw, encoded1));
        assertTrue(passwordEncoder.matches(raw, encoded2));
    }

    @Test
    @DisplayName("错误密码无法通过校验")
    void wrongPasswordShouldNotMatch() {
        String encoded = passwordEncoder.encode("correctPassword");
        assertFalse(passwordEncoder.matches("wrongPassword", encoded));
    }

    @Test
    @DisplayName("DataInitializer 应能识别出需要升级的明文密码")
    void plainPasswordShouldBeDetected() {
        SysUser user = new SysUser();
        user.setPassword("123456");   // 明文
        assertFalse(user.getPassword().startsWith("$2"), "明文密码不应以 $2 开头");

        String encoded = passwordEncoder.encode(user.getPassword());
        assertTrue(encoded.startsWith("$2"), "加密后应以 $2 开头");
    }
}