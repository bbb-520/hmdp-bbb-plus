package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;

@Data
@NoArgsConstructor  //生成无参构造方法
@AllArgsConstructor //生成全参构造方法
@Schema(description = "统一响应结果")
public class Result {

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "错误信息，成功时为 null", example = "手机格式错误")
    private String errorMsg;

    @Schema(description = "返回数据，类型随接口而定")
    private Object data;

    @Schema(description = "总记录数（分页查询时使用）")
    private Long total;

    /*
     * 成功的结果
     */
    public static Result ok(){
        return new Result(true, null, null, null);
    }
    /*
     * 成功的结果，带数据
     */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    /*
     * 成功的结果，带数据
     */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    /*
     * 失败的结果
     */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
