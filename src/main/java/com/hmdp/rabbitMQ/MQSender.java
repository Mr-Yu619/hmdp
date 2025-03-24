package com.hmdp.rabbitMQ;

import com.hmdp.config.RabbitMQTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String ROUTTINGKEY = "seckill.message";

    // 接收到的信息msg是订单信息
    // 发送秒杀信息
    public void sendSeckillMessage(String msg){
        log.info("发送消息"+msg);
        // 交换机，路由键，消息
        // 发送消息到路由器，路由器根据路由键来选择分发的消息队列
        rabbitTemplate.convertAndSend(RabbitMQTopicConfig.EXCHANGE,ROUTTINGKEY,msg);
    }
}
