package com.example.demo.utils;

import com.example.demo.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    //保存用户信息
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    //得到用户信息
    public static UserDTO getUser(){
        return tl.get();
    }

    //移除用户信息
    public static void removeUser(){
        tl.remove();
    }
}
