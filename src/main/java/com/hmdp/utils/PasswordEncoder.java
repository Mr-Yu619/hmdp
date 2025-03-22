package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/*
这是一个加密密码的类，也可以比较密码是否相同
 */
public class PasswordEncoder {
    public static String encode(String password){
        String salt = RandomUtil.randomString(20);
        return encode(password, salt);
    }
    public static String encode(String password, String salt){
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    //获取盐后比较加了盐的是不是一样的，不比较原来的密码了
    public static Boolean matches(String encodedPassword, String rawPassword){
        if(encodedPassword == null || rawPassword == null){
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("密码格式不正确！");
        }
        String[] arr = encodedPassword.split("@");
        String salt = arr[0];
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}
