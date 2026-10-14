package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@Slf4j
@MapperScan("com.example.demo.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class DemoApplication {

    /** Swagger UI 默认路径，实际以 application.yml 中 springdoc.swagger-ui.path 为准 */
    private static final String DEFAULT_SWAGGER_UI_PATH = "/swagger-ui.html";
    /** OpenAPI JSON 默认路径，实际以 application.yml 中 springdoc.api-docs.path 为准 */
    private static final String DEFAULT_API_DOCS_PATH = "/v3/api-docs";

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printSwaggerUrl(ApplicationReadyEvent event) {
        ApplicationContext context = event.getApplicationContext();
        Environment env = context.getEnvironment();

        // Swagger 被关闭时不打印
        if (!env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
            return;
        }

        String baseUrl = "http://localhost:" + resolvePort(context) + resolveContextPath(env);
        String swaggerUiPath = env.getProperty("springdoc.swagger-ui.path", DEFAULT_SWAGGER_UI_PATH);
        String apiDocsPath = env.getProperty("springdoc.api-docs.path", DEFAULT_API_DOCS_PATH);

        log.info("""

                        ============================================================
                          应用启动成功！Swagger 接口测试地址：
                          Swagger UI    : {}{}
                          OpenAPI JSON  : {}{}
                        ============================================================
                        """,
                baseUrl, swaggerUiPath, baseUrl, apiDocsPath);
    }

    /**
     * 获取 Web 服务器实际监听的端口。
     * 优先取已绑定的真实端口（可正确应对随机端口 / 命令行覆盖 port 的情况）。
     */
    private int resolvePort(ApplicationContext context) {
        if (context instanceof WebServerApplicationContext webContext) {
            return webContext.getWebServer().getPort();
        }
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        if (port != null) {
            return port;
        }
        return context.getEnvironment().getProperty("server.port", Integer.class, 8080);
    }

    /**
     * 获取项目访问上下文路径，未配置时为空串。
     */
    private String resolveContextPath(Environment env) {
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }
}
