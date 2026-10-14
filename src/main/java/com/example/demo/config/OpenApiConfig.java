package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OpenApiConfig {

    /**
     * 全局文档信息：标题、版本、描述、联系人、许可协议。
     */
    @Bean
    public OpenAPI demoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Demo 接口文档")
                        .version("v1.0.0")
                        .description("黑马点评练习项目接口文档，支持在页面直接发起请求完成接口测试。")
                        .contact(new Contact().name("bbb").email("bbb@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }


    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("00-全部接口")
                .pathsToMatch("/**")
                .build();
    }
}
