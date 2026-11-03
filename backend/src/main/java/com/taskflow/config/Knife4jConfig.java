package com.taskflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 * <p>启动后访问 http://localhost:8080/api/doc.html 可在线调试所有接口。</p>
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI taskFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskFlow 智能工单流转平台 接口文档")
                        .description("Spring Boot 3 + MyBatis-Plus + MySQL 8 全栈项目后端接口")
                        .version("1.0.0")
                        .contact(new Contact().name("TaskFlow Team"))
                        .description("""
                                默认账号：
                                - 超级管理员 admin / 123456（全部权限）
                                - 部门主管 manager / 123456（工单受理 + 审批）
                                - 普通员工 zhangsan / 123456（仅创建与查看）
                                所有接口需在 Header 中携带 Authorization: Bearer {token}
                                """));
    }
}