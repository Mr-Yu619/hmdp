package com.hmdp.utils;

/*
这是redis要用到的键的名称
 */
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType";
    public static final Long CACHE_LOGICAL_EXPIRE_TIME = 30L;

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String CACHE_FOLLOW = "follow:";

    public static final String SENDCODE_SENDTIME_KEY = "sms:sendtime:";

    public static final String ONE_LEVELLIMIT_KEY = "limit:onelevel:";
    public static final String TWO_LEVELLIMIT_KEY = "limit:twolevel:";
}
