package com.lessonweb.lesson.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public WebConfig(LessonProperties properties) {
        this.frontendOrigin = properties.frontendOrigin();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
