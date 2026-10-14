package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)   //自动生成 equals() 和 hashCode() 方法 表示在生成 equals() 和 hashCode() 时不包含父类的字段，即不调用父类的 equals() 和 hashCode()。这是默认值
@TableName("tb_user")   //指定数据库表名
@Accessors(chain = true)    //生成的 setter 方法返回当前对象（this），从而支持链式调用
public class User {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(exist = false)
    private String username;
    private String password;
    private String nickName;
    private String phone;
    private String icon = "";

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
