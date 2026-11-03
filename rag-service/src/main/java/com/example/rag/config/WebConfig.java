package com.example.rag.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.rag.model.DocumentStatus;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** ?status=ready works as well as ?status=READY */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, DocumentStatus.class,
                (Converter<String, DocumentStatus>) source -> DocumentStatus.valueOf(source.trim().toUpperCase(Locale.ROOT)));
    }
}
