package com.example.order.config;

import com.example.order.util.StringToEnumIgnoreCaseConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Accept enum query parameters in any case: status=confirmed. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumIgnoreCaseConverterFactory());
    }
}
