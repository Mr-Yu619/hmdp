package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController //等于ResponseBody+Controller
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @PostMapping("/code")
    public Result sentCode(@RequestParam("phone") String phone, HttpSession session) throws MessagingException {
        return userService.sendCode(phone, session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO,HttpSession session){
        return userService.login(loginFormDTO,session);
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO userDTO = UserHolder.getUser();
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        UserInfo info = userInfoService.getById(userId);
        if(info == null){
            return Result.ok();
        }
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long userId){
        User user = userService.getById(userId);
        if(user == null){
            // 没有详情，应该是第一次查看
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

}
