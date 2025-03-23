package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_FOLLOW;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = CACHE_FOLLOW + userId;
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        if(isFollow){
            return Result.ok(true);
        } else {
            Long count = query().eq("user_id", userId)
                    .eq("follow_user_id", followUserId).count();
            if(count>0){
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
            return Result.ok(count>0);
        }
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = CACHE_FOLLOW + userId;
        // 判断是关注还是取关
        if (isFollow){
            // 要关注
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId).setUserId(userId);
            boolean success = save(follow);
            if(success){
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else{
            // 要取关
            boolean success = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if(success){
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommons(Long follwoUserId) {
        Long userId = UserHolder.getUser().getId();
        String key1 = CACHE_FOLLOW + userId;
        String key2 = CACHE_FOLLOW + follwoUserId;

        Set<String> interSet = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(interSet == null || interSet.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = interSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = userService.listByIds(ids).stream().map(user ->
            BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
