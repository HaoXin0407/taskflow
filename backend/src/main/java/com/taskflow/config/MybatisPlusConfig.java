package com.taskflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>两个拦截器都必须在 MybatisPlusInterceptor 中手动注册，
 * 这是 MP 3.5.x 之后与老版本最大的区别，很多人踩过这个坑。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：生成 LIMIT 语句
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);      // 单页最大条数，防止恶意传 size=99999 拖垮数据库
        pagination.setOverflow(false);     // 页码越界时返回空列表而不是回到第一页
        interceptor.addInnerInterceptor(pagination);

        // 全表更新/删除拦截：阻止没有 where 条件的 update/delete，这是生产事故的高发点
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}