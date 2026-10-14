package com.example.demo.controller;

import com.example.demo.dto.Result;
import com.example.demo.service.IShopTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "商品类型", description = "查询商铺类型列表")
@RestController
@RequestMapping("/shop/type")
public class ShopTypeController {
    @Resource
    private IShopTypeService shopTypeService;

    @Operation(summary = "查询商铺类型列表")
    @GetMapping("list")
    public Result queryTypeList() {
        return shopTypeService.queryList();
    }
}
