package com.example.product.config;

import com.example.product.util.StringToEnumIgnoreCaseConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Accept enum query parameters in any case: sortBy=price, sortBy=PRICE, sortDirection=desc. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new StringToEnumIgnoreCaseConverterFactory());
    }
}
