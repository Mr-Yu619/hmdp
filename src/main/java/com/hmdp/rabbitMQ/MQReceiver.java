package com.hmdp.rabbitMQ;

import com.alibaba.fastjson.JSON;
import com.hmdp.config.RabbitMQTopicConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
@Slf4j
public class MQReceiver {

    // voucherOrder 优惠券是订单信息,有优惠券id，用户id，下单时间等等
    @Resource
    private IVoucherOrderService voucherOrderService;

    // secKillVoucher 是优惠券的使用信息，有库存，开始时间，结束时间等

    // Voucher 是优惠券的基本信息，比如优惠力度，属于哪家商铺
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    // 这里直接监听一个队列，然后有的话直接获取
    @Transactional
    @RabbitListener(queues = RabbitMQTopicConfig.QUEUE)
    public void receiveSeckillMessage(String msg){
        log.info("接收到消息：" +msg);
        // msg是订单信息的Json格式，现在把它转化为Entity对象
        VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);
        // 获取订单id
        Long voucherId = voucherOrder.getVoucherId();
        // 获取用户id
        Long userId = voucherOrder.getUserId();
        // 查询订单看一下是否存在, 因为redis也会失效，数据库才是最保准的
        Long count = voucherOrderService.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if(count > 0){
            // 用户已经购买过了
            log.error("该用户已经购买过");
            return;
        }
        log.info("扣减库存");
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }
        // 订单信息是在这里更新的，
        voucherOrderService.save(voucherOrder);
    }
}
