package com.taskflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TaskFlow 启动类
 *
 * @author taskflow
 */
@SpringBootApplication
@MapperScan("com.taskflow.mapper")
@EnableScheduling          // 开启定时任务（SLA 超时扫描）
public class TaskFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskFlowApplication.class, args);
        System.out.println("""

                ====================================================
                  TaskFlow 后端启动成功！
                  接口地址: http://localhost:8080/api
                  接口文档: http://localhost:8080/api/doc.html
                ====================================================
                """);
    }
}