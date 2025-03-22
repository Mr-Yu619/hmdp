package com.hmdp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.swing.*;


/*将一个MybatisPlusIntercepter拦截器对象注册为Bean，当在查询中使用分页时，这个拦截器会自动修改SQL语句
Spring MVC的拦截器是处理HTTP请求的，属于Web层；而MyBatis的拦截器是处理SQL执行的 这个拦截器也不是自己定义的*/
@Configuration
/*
标记配置类，告诉Spring这个类用来定义配置
定义Bean，通过@Bean方法注册组件到容器
替代XNL 用Java代码配置，代替传统XML
单例管理 默认单例，确保多次调用返回同一单例
 */
public class MybatisConfig {
    @Bean
    /*
    在配置类中，用 @Bean 标注的方法会将返回值注册为 Spring 容器管理的 Bean。
    默认情况下，@Bean 方法返回的 Bean 是单例的。
     */
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
