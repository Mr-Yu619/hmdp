package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
/*
这是一个redis的工具类，用于查询任何数据类型，redis就是用来查的，查不到的自动处理逻辑
有逻辑过期查询 和 互斥查询， 二者都加了缓存空值的策略来解决缓存穿透问题
 */


@Slf4j
@Component
public class CacheClient {
    @Resource
    //这里使用stringRedisTemplate是因为任何对象都可以先转化为Json字符串存入redis中
    private StringRedisTemplate stringRedisTemplate;
    //创建了一个静态线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 方法1: 将任意Java对象序列化为JSON，并存储到value为String类型的redis对象中，并可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    // 方法2：将任意Java对象序列化为JSON，并存储到value为String类型的redis对象中，并可以设置逻辑过期时间，用于处理击穿问题
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData<Object> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 方法3: 根据指定的Key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit){
        /*
        Class<R> 表示一个 泛型类型参数化的 Class 对象，表示type是一个R类型的Class对象，而不是一个R类型的实例
         */


        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果不为空（查询到了），则转为R类型直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //到了这一步，要么为null，要么为空，如果不为null，那就是自己写进去的‘’，缓存的空值，直接返回
        if(json != null){
            return null;
        }
        //到这一步，就是null，此时redis中是真没有，看一眼数据库
        //直接调用fallback，不能写死，传入的函数是变化的
        R r = dbFallback.apply(id);

        //如果没查到
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //查到了则转为json字符串
        this.set(key, r, time, timeUnit);
        return r;
    }

    // 方法4: 根据指定的key查询缓存，并发序列化为指定类型，是带逻辑过期时间的查询,同时加上了缓存空值
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,Function<ID, R> dbFallback, Long time, TimeUnit timeUnit){
        //1. 从redis中查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 如果查到了
        if(StrUtil.isNotBlank(json)){
            //先取出data
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            LocalDateTime expireTime = redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
                // 3. 未过期，直接返回
                return r;
            }
            //4. 过期，尝试获取互斥锁进行更新
            String lockKey = LOCK_SHOP_KEY + id;
            boolean flag = tryLock(lockKey);
            // 5. 如果获取到了锁
            if(flag){
                //开启独立线程
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try{
                        R tmp = dbFallback.apply(id);
                        this.setWithLogicExpire(key, tmp, time, timeUnit);
                    } catch(Exception e){
                        throw new RuntimeException(e);
                    } finally {
                        unLock(lockKey);
                    }
                });
                return r;
            }

            // 6.没获取到锁直接返回
            return r;
        }
        // 7. 没查到，说明缓存中可能暂时没有，没有建好呢
        // 8. 如果查到""值,说明不要，防止缓存穿透
        if(json != null){
            return null;
        }
        // 9. 查数据库
        R r = dbFallback.apply(id);

        // 10.如果是null值,写入空值
        if(r == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 11. 查到了则存起来，带逻辑过期时间的
        this.setWithLogicExpire(key, r, time, timeUnit);
        return r;
    }


    // 方法5 互斥查询, 也是为了解决缓存击穿问题的

    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit){
        // 先从Redis中查
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果查到了
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 缓存的空对象
        if(json != null){
            return null;
        }
        // 接下里就剩下null对象了
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        R r = null;
        try{
            if(!flag){
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            r = dbFallback.apply(id);
            if(r == null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, r, time, timeUnit);
        } catch (Exception e){
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return r;
    }
    // 获取锁对象
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 删除锁对象
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
