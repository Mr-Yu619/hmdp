package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MailUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession httpSession) throws MessagingException {
        /*
        进来先看是不是在一级限制或者二级限制内，如果在直接返回错误信息
        如果不在，那么统计一下最近1分钟内，和最近5分钟内的发送验证码的次数，如果达到了限制条件，那么标记好限制，然后返回
        如果次数不够达成限制，那么可以生成验证码，保存验证码到redis中，然后在添加一下发送验证码的时间，保存在一个ZSet中，外部键为特定的字符串
        ZSet的元素是时间戳 值也是时间戳，统计发送验证码的次数的时候就是用的范围统计count
         */
        // 0. 看看手机号格式正确不
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }

        // 1.判断是不是在一级限制条件内
        Boolean oneLevelLimit = stringRedisTemplate.opsForSet().isMember(ONE_LEVELLIMIT_KEY + phone,"exist");
        if(oneLevelLimit!=null && oneLevelLimit){
            // 在一级限制内，不能发验证码
            Result.fail("您需要等5分钟再请求");
        }
        // 2.判断是否在二级限制内
        Boolean twoLevelLimit = stringRedisTemplate.opsForSet().isMember(TWO_LEVELLIMIT_KEY + phone, "exist");
        if(twoLevelLimit!=null && twoLevelLimit){
            // 在二级限制内，不能发验证码
            Result.fail("您需要等20分钟再请求");
        }
        // 3.检查在过去一分钟内发送验证码的次数
        long oneMinuteAgo = System.currentTimeMillis() - 60*1000;
        long count_oneMinute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + phone, oneMinuteAgo, System.currentTimeMillis());
        if(count_oneMinute >= 1){
            // 过去一分钟内发送了一次验证码，不能再次发送验证码
            return Result.fail("距离上次发送时间不足1分钟，请1分钟后重试");
        }

        // 4.检查在过去5分钟内发送验证码的次数
        long fiveMinuteAgo = System.currentTimeMillis() - 60*5*1000;
        long count_fiveMinute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + phone, fiveMinuteAgo, System.currentTimeMillis());
        if(count_fiveMinute == 5){
            stringRedisTemplate.opsForSet().add(ONE_LEVELLIMIT_KEY + phone, "exist");
            stringRedisTemplate.expire(ONE_LEVELLIMIT_KEY + phone, 5, TimeUnit.MINUTES);
            return Result.fail("5分钟内已经发送了5次，接下来如需再发送请等待5分钟后重试");
        } else if(count_fiveMinute > 5 && count_fiveMinute%3 == 2){
            stringRedisTemplate.opsForSet().add(TWO_LEVELLIMIT_KEY + phone, "exist");
            stringRedisTemplate.expire(TWO_LEVELLIMIT_KEY + phone, 20, TimeUnit.MINUTES); //在这里空置限制的时间
            return Result.fail("接下来如需再发送，请等20分钟后再请求");
        }

        String code = MailUtils.achieveCode();

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("发送登录验证码：{}", code);

        stringRedisTemplate.opsForZSet().add(SENDCODE_SENDTIME_KEY + phone, System.currentTimeMillis()+"", System.currentTimeMillis());

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session){
        String phone = loginFormDTO.getPhone();
        String code = loginFormDTO.getCode();
        // 检查手机号格式是否正确，不同的请求就应该再次去确认
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        // 从redis中拿出验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 如果找不到或者不匹配那么返回错误
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("无效验证码");
        }
        //如果匹配了，那么看一下用户有没有这个手机号的
        User user = query().eq("phone", phone).one();
        //如果没有, 创建一个新用户（新用户直接注册了）
        if(user==null){
            user = createUser(phone);
        }

        //接下来要保存用户信息到Redis中
        //这个是为每次登陆，分给一个用户的一个随机token，这个随机的token要返回给前端的，以后登录验证的时候拿这个token来找redis中的数据，前端以后每次访问都要带上这个token
        String token = UUID.randomUUID().toString();

        // 把userDTO对象转化为HashMap存储，因为要放到redis中存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        HashMap<String, String> userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());

        // 把userMap对象存储到redis中
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 登陆成功后就直接删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是当月第几天（1-31）
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入redis，这是一个位矢图，一共31位，哪一天就把对应位设置为1
        // BITSET key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();

        //5. 获取截止到今日的签到记录
        //BITFIELD key GET uDay 0

        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands
                                .BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if(result==null || result.isEmpty()){
            return Result.ok(0);
        }
        //
        int count = 0;
        Long num = result.get(0);
        while(true){
            if((num & 1) ==0){
                break;
            }
            else {
                count++;
                num = num >>> 1;
            }
        }
        return Result.ok(count);
    }

    public User createUser(String phone){
        User user = new User().setPhone(phone).setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
