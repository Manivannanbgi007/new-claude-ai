package com.example.user.config;

import com.example.user.util.StringToEnumIgnoreCaseConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Accept enum query parameters in any case: status=active, sortBy=last_name, sortDirection=desc. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumIgnoreCaseConverterFactory());
    }
}
