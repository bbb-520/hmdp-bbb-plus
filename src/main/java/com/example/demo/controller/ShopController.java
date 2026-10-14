package com.example.demo.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dto.Result;
import com.example.demo.entity.Shop;
import com.example.demo.service.IShopService;
import com.example.demo.utils.SystemConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
@Tag(name = "商铺信息", description = "根据id查询商铺信息、新增、跟新商铺数据、根据类型、根据关键字分页查询")
public class ShopController {
    @Resource
    public IShopService shopService;

    @Operation(summary = "根据id查询商铺信息")
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id){
        return shopService.queryById(id);
    }

    @Operation(summary = "新增商铺信息")
    @PostMapping
    public Result saveShop(@RequestBody Shop shop){
        //写入数据库
        shopService.save(shop);
        //返回店铺id
        return Result.ok(shop.getId());
    }

    @Operation(summary = "更新商品信息")
    @PutMapping
    public Result updateShop(@RequestBody Shop shop){
        return shopService.update(shop);
    }

    @Operation(summary = "根据类型分页查询")
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1")Integer current
    ){
        //根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        //返回数据
        return Result.ok(page.getRecords());
    }

    @Operation(summary = "根据关键字分页查询")
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ){
        //根据关键字分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
