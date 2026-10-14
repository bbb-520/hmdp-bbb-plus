package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data

@Schema(description = "登录表单")
public class LoginFormDTO {

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String phone;

    @Schema(description = "验证码（6 位数字）", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    private String code;

    @Schema(description = "密码（当前登录方式未使用）")
    private String password;
}
