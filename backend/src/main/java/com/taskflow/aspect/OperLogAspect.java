package com.taskflow.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.annotation.OperLog;
import com.taskflow.entity.SysOperLog;
import com.taskflow.mapper.SysOperLogMapper;
import com.taskflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面 —— AOP 的经典应用场景。
 *
 * <p>价值：把「记录操作日志」这件事从每个 Controller 里抽出来，
 * 业务代码零侵入，新增接口只需加一个注解。这是面试中「AOP 实际应用」的加分点。</p>
 *
 * <p>实现要点：</p>
 * <ol>
 *     <li>{@code @Around} 环绕通知，能拿到方法执行前后的信息</li>
 *     <li>记录耗时、请求参数、返回结果、异常信息</li>
 *     <li>参数序列化时过滤掉 MultipartFile / Servlet 相关对象，否则序列化会报错</li>
 *     <li>日志写入失败不能影响主流程，所以 catch 住所有异常</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();

        SysOperLog entity = new SysOperLog();
        entity.setTitle(operLog.title());
        entity.setBusinessType(operLog.businessType().name());
        entity.setOperTime(LocalDateTime.now());
        entity.setStatus(1);

        // 请求相关信息
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            entity.setRequestMethod(request.getMethod());
            entity.setOperUrl(request.getRequestURI());
            entity.setOperIp(resolveIp(request));
        }

        // 操作人
        try {
            entity.setOperId(SecurityUtils.getUserIdOrNull());
            entity.setOperName(SecurityUtils.getNickname());
        } catch (Exception ignored) {
            entity.setOperName("未知");
        }

        // 方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        entity.setMethod(signature.getDeclaringTypeName() + "." + signature.getName() + "()");

        // 请求参数
        try {
            entity.setOperParam(truncate(serializeArgs(joinPoint.getArgs()), 2000));
        } catch (Exception e) {
            entity.setOperParam("参数序列化失败: " + e.getMessage());
        }

        // 执行目标方法
        Object result;
        try {
            result = joinPoint.proceed();
            entity.setStatus(1);
        } catch (Throwable e) {
            entity.setStatus(0);
            entity.setErrorMsg(truncate(e.getMessage(), 2000));
            entity.setCostTime(System.currentTimeMillis() - start);
            saveLog(entity);
            throw e;
        }

        // 记录返回结果与耗时
        try {
            entity.setJsonResult(truncate(serialize(result), 2000));
        } catch (Exception ignored) {
            // 序列化失败不影响主流程
        }
        entity.setCostTime(System.currentTimeMillis() - start);
        saveLog(entity);

        return result;
    }

    /**
     * 异步思路：日志写库失败绝不能影响主业务。
     */
    private void saveLog(SysOperLog entity) {
        try {
            operLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("操作日志写入失败", e);
        }
    }

    /**
     * 序列化参数，过滤掉无法序列化的类型。
     */
    /**
     * 序列化参数，过滤掉无法序列化的类型。
     * <p>ObjectMapper.writeValueAsString 声明的是受检异常 JsonProcessingException，
     * 这里统一捕获后返回错误提示，不让日志记录失败影响主流程。</p>
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> list = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MultipartFile
                    || arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                continue;
            }
            list.add(arg);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[参数序列化失败: " + e.getOriginalMessage() + "]";
        }
    }

    /**
     * 序列化返回值，失败时返回提示文案而不是抛异常。
     */
    private String serialize(Object obj) {
        if (obj == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[返回值序列化失败: " + e.getOriginalMessage() + "]";
        }
    }

    /**
     * 截断过长字符串，避免超出数据库字段长度。
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）。
     */
    private String resolveIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"，取第一个
                int idx = ip.indexOf(',');
                return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}