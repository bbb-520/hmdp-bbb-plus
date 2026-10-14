package com.example.demo.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    //过期时间
    private LocalDateTime expireTime;
    //存进Redis中的数据
    private Object data;
}
