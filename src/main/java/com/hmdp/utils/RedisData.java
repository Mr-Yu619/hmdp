package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/*
Redis 数据库中的对应对象
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
