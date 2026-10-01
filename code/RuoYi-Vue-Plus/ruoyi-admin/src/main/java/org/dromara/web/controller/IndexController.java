package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页
 *
 * @author Lion Li
 */
@SaIgnore
@RequiredArgsConstructor
@RestController
public class IndexController {

    /**
     * 访问首页，提示语
     */
    @GetMapping("/")
    public String index() {
        // 后端根路径的提示语（域名配错时用户可能直接看到）：不带上游框架名
        return "类器官样本管理系统接口服务，请通过前端地址访问。";
    }

}
