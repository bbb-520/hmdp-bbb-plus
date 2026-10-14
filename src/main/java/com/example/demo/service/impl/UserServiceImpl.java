package com.example.demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.dto.LoginFormDTO;
import com.example.demo.dto.Result;
import com.example.demo.dto.UserDTO;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.IUserService;
import com.example.demo.utils.RegexUtils;
import com.example.demo.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.example.demo.utils.RedisConstants.*;
import static com.example.demo.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.检验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
        //2.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送验证码：{}给{}", code, phone);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        //1.验证手机号
        String phone = loginFormDTO.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
        //2.从redis中取出验证码并验证
        String casheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginFormDTO.getCode();
        if (casheCode == null || !casheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        //3.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //4.判断用户是否存在
        if (user == null) {
            //5.若不存在则新建用户并保存
            user = createUserWithPhone(phone);
        }
        //6.报错用户信息到redis
        //6.1随机生成token，作为哦登录令牌（UUID）
        String token = UUID.randomUUID().toString();
        //6.2将User对象转化为Hash再转化为Map
        //User 是数据库实体，可能包含密码、手机号、创建时间等敏感或不需要缓存的字段。
        //UserDTO 通常只保留登录后需要用的信息
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //Map 是 Java 端对 Redis Hash 的操作形式，Redis 实际存的是 Hash
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fidleValue) -> fidleValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //6.3设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    //TODO 退出功能
    @Override
    public Result logout() {

        return null;
    }

    /*
    SETBIT 是 Redis 位图操作，用来把某个二进制位设为 0 或 1
    每个用户每个月一个 key；
    每个 bit 代表一天；
    签到就把对应天数的 bit 设为 1；
    省内存、查询快、统计方便、适合高并发。
     */
    @Override
    public Result sign() {
        //1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis SETBIT key offset
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        var key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.获取本月截至今天所有的签到记录，返回的是一个十进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }
        //6.循环遍历
        int count = 0;
        while (true){
            //6.1.让这个数字与1做位运算，得到数字的最后一个bit位，并判断这个bit位是否为0
            if ((num & 1) == 0){
                //6.2若为0说明未签到，结束
                break;
            }else {
                //6.3若不为0，说明以签到，计数器 + 1
                count ++;
            }
            //6.4.把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);

    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX +  RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
