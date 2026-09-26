package org.dromara.common.web.config;

import jakarta.servlet.DispatcherType;
import org.dromara.common.web.config.properties.XssProperties;
import org.dromara.common.web.filter.RepeatableFilter;
import org.dromara.common.web.filter.UploadSizeLimitFilter;
import org.dromara.common.web.filter.XssFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Filter配置
 *
 * @author Lion Li
 */
@AutoConfiguration
@EnableConfigurationProperties(XssProperties.class)
public class FilterConfig {

    @Bean
    @ConditionalOnProperty(value = "xss.enabled", havingValue = "true")
    @FilterRegistration(
        name = "xssFilter",
        urlPatterns = "/*",
        order = FilterRegistrationBean.HIGHEST_PRECEDENCE + 1,
        dispatcherTypes = DispatcherType.REQUEST
    )
    public XssFilter xssFilter() {
        return new XssFilter();
    }

    @Bean
    @FilterRegistration(name = "repeatableFilter", urlPatterns = "/*")
    public RepeatableFilter repeatableFilter() {
        return new RepeatableFilter();
    }

    /**
     * 上传体积闸（V05）：排在最前，任何人读请求体之前按 Content-Length 判掉超限的 multipart 请求。
     */
    @Bean
    public FilterRegistrationBean<UploadSizeLimitFilter> uploadSizeLimitFilter(
        ObjectProvider<MultipartProperties> multipartProperties) {
        FilterRegistrationBean<UploadSizeLimitFilter> registration =
            new FilterRegistrationBean<>(new UploadSizeLimitFilter(multipartProperties.getIfAvailable()));
        registration.setName("uploadSizeLimitFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        return registration;
    }

}
