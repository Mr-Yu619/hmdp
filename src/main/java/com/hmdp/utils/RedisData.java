package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/*
Redis 数据库中的对应对象,加上了逻辑过期时间
 */
@Data
public class RedisData<T> {
    private LocalDateTime expireTime;
    private T data;
}
