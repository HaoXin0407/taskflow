package com.taskflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工单业务配置。
 *
 * <p><b>为什么用 {@code @ConfigurationProperties} 而不是 {@code @Value}？</b></p>
 *
 * <p>我最初写的是：</p>
 * <pre>
 *   &#64;Value("${taskflow.ticket.sla-hours}")
 *   private Map&lt;Integer, Integer&gt; slaHours;
 * </pre>
 *
 * <p>启动时报错：{@code Could not resolve placeholder 'taskflow.ticket.sla-hours'}。</p>
 *
 * <p><b>原因</b>：application.yml 里 sla-hours 是个嵌套结构（下面还有 1/2/3/4 四个子键），
 * 而 {@code @Value} 只能解析单个标量值，遇到嵌套结构就无从下手。</p>
 *
 * <p><b>解法</b>：用 {@code @ConfigurationProperties} 做类型安全的配置绑定。
 * 它能把 YAML 结构直接映射成 Java 对象，而且：</p>
 * <ul>
 *     <li>配置项集中管理，改一处即可</li>
 *     <li>IDE 有自动补全，不容易写错 key</li>
 *     <li>嵌套结构支持良好</li>
 * </ul>
 *
 * <p><b>面试可讲</b>：这是 Spring Boot 3 推荐的配置绑定方式，
 * 相比 @Value 的优势是类型安全 + 支持复杂结构 + 弱耦合（改配置不用改代码）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "taskflow.ticket")
public class TicketProperties {

    /**
     * 各优先级工单的 SLA 处理时长（小时）。
     * key = 优先级（1紧急 2高 3中 4低），value = 时长。
     * <p>用 LinkedHashMap 保证读取顺序稳定。</p>
     */
    private Map<Integer, Integer> slaHours = new LinkedHashMap<>(Map.of(
            1, 24,    // 紧急：1 天内处理
            2, 72,    // 高：3 天内
            3, 168,   // 中：7 天内
            4, 336    // 低：14 天内
    ));

    /**
     * 取指定优先级的 SLA 时长，缺省返回 168 小时（7 天）。
     */
    public int getSlaHours(Integer priority) {
        if (priority == null || slaHours == null) {
            return 168;
        }
        return slaHours.getOrDefault(priority, 168);
    }
}