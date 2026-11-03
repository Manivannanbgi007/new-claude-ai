package com.example.mcpclient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the chat UI under its own path prefix, /chat/.
 * A unique prefix lets the API Gateway route "/chat/**" to this app without
 * claiming "/", "/css/**" or "/js/**" for the whole platform.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /chat/ shows the page. The trailing slash matters: the page loads
        // "css/chat.css" relative to its own URL, i.e. /chat/css/chat.css.
        registry.addViewController("/chat/").setViewName("forward:/chat/index.html");
        // /chat (no slash) and / (direct access on port 8091) lead to /chat/
        registry.addViewController("/chat").setViewName("redirect:/chat/");
        registry.addViewController("/").setViewName("redirect:/chat/");
    }
}
