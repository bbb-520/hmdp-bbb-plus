package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.dto.Result;
import com.example.demo.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {

    Result queryList();
}
