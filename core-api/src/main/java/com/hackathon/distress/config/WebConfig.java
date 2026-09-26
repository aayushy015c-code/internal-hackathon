package com.hackathon.distress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebConfig.java
 * --------------
 * Security hygiene item: "CORS limited to our localhost ports."
 *
 * By default, a browser blocks JavaScript on one origin (e.g. the frontend
 * dev server at http://localhost:5500) from calling an API on a different
 * origin (http://localhost:8080) unless the API explicitly allows it. This
 * class is that explicit allow-list, read from application.properties so
 * you don't have to touch Java code if your frontend's port changes.
 *
 * We do NOT use "*" (allow everyone) because this API can create alerts and
 * read contact/location data - it should only ever be called from our own
 * frontend running on the same machine.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
