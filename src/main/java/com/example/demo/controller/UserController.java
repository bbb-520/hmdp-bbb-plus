package com.example.demo.controller;

import cn.hutool.core.bean.BeanUtil;
import com.example.demo.dto.LoginFormDTO;
import com.example.demo.dto.Result;
import com.example.demo.dto.UserDTO;
import com.example.demo.service.IUserInfoService;
import com.example.demo.service.IUserService;
import com.example.demo.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "用户接口", description = "发送验证码、登录等用户相关接口")
@RestController     //@Controller + @ResponseBody 返回JSON数据
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;

    /** 发送验证码 */
    @Operation(summary = "发送手机验证码")
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /** 登录 / 注册：用户不存在则自动注册，返回 token */
    @Operation(summary = "登录 / 注册")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO, HttpSession session) {
        return userService.login(loginFormDTO, session);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result logout(){
        return userService.logout();
    }

    @Operation(summary = "获取当前登录的用户")
    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @Operation(summary = "根据用户 id 查询用户公开信息的接口")
    @GetMapping("/{id}")
    public Result info(@PathVariable("id")Long userId){
        var user = userInfoService.getById(userId);
        if (user == null){
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @Operation(summary = "用户签到")
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @Operation(summary = "记录签到总数")
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}