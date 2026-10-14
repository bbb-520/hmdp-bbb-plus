package com.example.demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.controller.ShopTypeController;
import com.example.demo.dto.Result;
import com.example.demo.entity.ShopType;
import com.example.demo.mapper.ShopTypeMapper;
import com.example.demo.service.IShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.demo.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        //1.先从redis中查询是否有商铺
        List<String> shopTypes = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.查到了则转为ShopType类型返回
        if (!shopTypes.isEmpty()){
            List<ShopType> tmp = shopTypes.stream()
                    .map(type -> JSONUtil.toBean(type, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(tmp);
        }
        //3.否则就去查数据库
        List<ShopType> tmp = query().orderByAsc("sort").list();
        if (tmp == null){
            return Result.fail("商户不存在");
        }
        //4.存在则转化为JSON字符串存入Redis
        shopTypes = tmp.stream()
                .map(JSONUtil::toJsonStr)
                .toList();
        return Result.ok(tmp);
    }
}
























