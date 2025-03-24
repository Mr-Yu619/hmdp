package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.rabbitMQ.MQSender;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.google.common.util.concurrent.RateLimiter;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    // 秒杀的逻辑应该是这样的
    /*
    加了一个令牌桶算法，进行一个限流
    先访问缓存，这时候有三种情况
    0.缓存中没有这个键，此时redis中还没有这个数据（其实这一步可以在添加秒杀优惠券的时候就放进去，然后设置逻辑过期，但问题是没设计这个接口md）
    1.缓存中有这个键，但是用户没有购买权限
    2.缓存中有这个键，且有购买权限，但是库存不够了
    3.缓存中有这个键，且有购买权限，买了，更新缓存了
    访问完缓存，建一个订单项，交给消息队列，让消息队列自己去处理

    那么redis在这里的作用是什么呢？
    快速判断是否满足资格，对于不成功的操作会加快处理时间，成功的操作就是与操作数据库一样的处理时间
     */

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MQSender mqSender;

    // 每秒生成10个许可
    private RateLimiter rateLimiter = RateLimiter.create(10);

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private RedisIdWorker redisIdWorker;


    // 就是想买这个voucher，提供了voucherId
    @Override
    public Result seckillvoucher(Long voucherId) {
        // 令牌桶算法，限流
        // 尝试在1s内获得许可，获得返回true，没获得返回false
        // 如果有许可立刻返回
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)){
            return Result.fail("目前网络正忙，请重试");
        }
        Long userId = UserHolder.getUser().getId();

        // 1.执行Lua脚本，判断库存和购买资格，进行扣减库存和购买资格核销
        Long r = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        // 2.判断返回结果
        int result= r.intValue();
        if(result != 0){
            // 2.1不为0 代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "该用户重复下单");
        }
        // 2.2 为0 代表有购买资格，将下单信息保存到阻塞队列

        // 2.3 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.4 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.5 用户id
        voucherOrder.setUserId(userId);
        // 2.6 代金券id
        voucherOrder.setVoucherId(voucherId);

        // 2.7 将信息放入MQ中
        mqSender.sendSeckillMessage(JSON.toJSONString(voucherOrder));

        return Result.ok(orderId);
    }
}
