package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController //等于ResponseBody+Controller
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @PostMapping("code")
    public Result sentCode(@RequestParam("phone") String phone, HttpSession session){
        // TODO 发送短信验证码并保存验证码
        return Result.fail("功能未完成");
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO,HttpSession session){
        // TODO 实现登陆功能
        return Result.fail("功能未完成");
    }

    @PostMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        return Result.fail("功能未完成");
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        UserInfo info = userInfoService.getById(userId);
        if(info == null){
            return Result.ok();
        }
        return Result.ok(info);
    }
}
