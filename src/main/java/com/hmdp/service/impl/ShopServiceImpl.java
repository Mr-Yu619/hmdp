package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //这里需要声明一个线程池，因为下面缓存击穿问题，我们需要新建一个线程来完成重构缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;


    @Override
    public Result queryById(Long id) {
        //解决缓存穿透的代码逻辑
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    // 解决缓存穿透的代码
    private Shop queryWithChuanTou(Long id) {
        //先从redis中查
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 剩下一种空，一种null，如果为空直接返回，代表数据库中肯定没有
        if(shopJson != null){
            return null;
        }

        // 否则查询数据库，如果这个数据不存在，那么将空值写入缓存，如果存在，则写入redis中，返回
        Shop shop = getById(id);
        if(shop == null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    // 互斥锁解决缓存击穿，同时携带了缓存空值
    private Shop queryWithJiChuan_mutex(Long id){
        // 先从redis中查
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        // 剩下一种空一种null，如果为空直接返回，代表数据库中肯定没有
        if(shopJson != null){
            return null;
        }
        // 接下来要进行缓存重建
        // 首先获取锁，然后如果没有获取到，休眠，自我阻塞，如果获取到了，那么开始重建流程
        // 如果没有，写入空值,如果获取到了，写入redis即可
        Shop shop = null;
        try {
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
            while (!flag){
                Thread.sleep(50);
                return queryWithJiChuan_mutex(id);
            }
            shop = getById(id);
            if(shop == null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            shopJson = JSONUtil.toJsonStr(shop);
            // 获取到了写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e){
            throw new RuntimeException(e);
        } finally {
            unLock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    private Shop queryWithLogicalExpire(Long id){
        // 先查缓存，如果有，再看过期与否，如果没过期，直接返回，如果过期了，获取锁，开始重建
        // 如果获取不到锁，直接返回旧数据，如果获取到了锁，那么新建一个任务来进行重建，就是从数据库中找，如果没找到，缓存空值，如果找到，缓存新数据，并且返回旧数据
        // 如果缓存中没有，则看是不是空值，如果是，直接返回，如果不是，查数据库，如果数据库中没有，写入空值到缓存，如果有，写入redis
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shopJson)){
            RedisData<Shop> redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {}, false);
            LocalDateTime expiretime = redisData.getExpireTime();
            Shop shop = redisData.getData();
            if(expiretime.isAfter(LocalDateTime.now())){
                return shop;
            }
            // 过期后的操作
            try{
                boolean flag = tryLock(LOCK_SHOP_KEY + id);
                // 没获取到锁
                if(!flag){
                    return shop;
                }
                // 获取到了锁
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    rebuildRedis(id, CACHE_LOGICAL_EXPIRE_TIME);
                });
                return shop;
            } catch (Exception e){
                throw new RuntimeException(e);
            } finally {
                unLock(LOCK_SHOP_KEY + id);
            }
        }
        // 在Redis中没查到数据，两种情况，一种null一种空
        if(shopJson != null){
            return null;
        }
        // 缓存重建
        Shop shop = getById(id);
        if(shop == null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        rebuildRedis(shop.getId(), CACHE_LOGICAL_EXPIRE_TIME);
        return shop;
    }

    private void rebuildRedis(Long id, Long cacheLogicalExpireTime) {
        Shop shop = getById(id);
        RedisData<Shop> tmp = new RedisData<>();
        tmp.setData(shop);
        tmp.setExpireTime(LocalDateTime.now().plusSeconds(cacheLogicalExpireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(tmp));
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result update(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 先更新数据库再删除缓存
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }


    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        // 修改后的代码逻辑
        if (results == null || results.getContent().isEmpty()) {
            // 从数据库查询该类型的所有店铺（非分页）
            List<Shop> dbShops = query().eq("type_id", typeId).list();

            if (!dbShops.isEmpty()) {
                // 将数据库查询到的店铺写入 Redis GEO
                List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
                for (Shop shop : dbShops) {
                    if (shop.getX() != null && shop.getY() != null) {
                        locations.add(new RedisGeoCommands.GeoLocation<>(
                                shop.getId().toString(),
                                new Point(shop.getX(), shop.getY())
                        ));
                    }
                }
                if (!locations.isEmpty()) {
                    stringRedisTemplate.opsForGeo().add(key, locations);
                }
            }

            // 返回分页数据（根据业务需求选择是否分页）
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
