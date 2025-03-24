-- 1.参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1库存key 是一个字符串,代表这个优惠券的库存
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2订单key 是一个集合set，代表这个优惠券id对应的用户
local orderKey = 'seckill:order:' .. voucherId
-- 订单key对应值为用户id

--3.脚本业务
--3.1判断库存是否充足
if(tonumber(redis.call('get', stockKey) <= 0)then
    -- 3.2库存不足 返回1
    return 1
end

-- 3.2判断用户是否下过单
if(redis.call('sismember', orderKey,userId) == 1) then
    -- 3.3用户存在，说明重复下单
    return 2then
end

-- 3.4扣库存
redis.call('incrby',stockKey,-1)
-- 3.5下单并保存用户
redis.call('sadd',orderKey,userId)
-- 返回结果0代表成功
return 0