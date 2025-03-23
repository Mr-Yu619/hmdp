package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Override
    public Result queryList(){
        // 先从Redis中查
        List<String> shopTypes =
                stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        // 如果不空，转换一下返回
        if(!shopTypes.isEmpty()){
            List<ShopType> tmp = new ArrayList<>();
            for (String st : shopTypes) {
                ShopType shopType = JSONUtil.toBean(st, ShopType.class);
                tmp.add(shopType);
            }
            return Result.ok(tmp);
        }

        // 如果空，查数据库
        List<ShopType> tmp = query().orderByAsc("sort").list();
        if(tmp == null || tmp.isEmpty()){
            return Result.fail("店铺类型不存在");
        }



        for (ShopType shopType : tmp) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypes.add(jsonStr);
        }

        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_KEY, shopTypes);

        return Result.ok(tmp);
    }
}
