package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
这也是一个拦截器, 但是他不会拦截任何东西，他要做的就是把redis里的东西转化为DTO保存在threadLocal中
还有就是更新缓存时间
 */
public class RefreshTokenIntercepter implements HandlerInterceptor {

    // 这里不是自动装配，因为RefreshTokenIntercepter是我们手动在WebConfig里new出来的
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenIntercepter(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        // 2.如果token为空，直接放行，交给LoginIntercepter处理
        if(StrUtil.isBlank(token)){
            return true;
        }
        // 这里的用户信息key是这样构成的
        String key = RedisConstants.LOGIN_USER_KEY + token;
        // 3.基于token获取redis中的用户数据，用户数据是map
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 4.判断用户是否存在，不存在也放行，交给LoginIntercepter
        if(userMap.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
