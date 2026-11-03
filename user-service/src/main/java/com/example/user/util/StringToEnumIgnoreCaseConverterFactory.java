package com.example.user.util;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import java.util.Arrays;

/**
 * Converts request parameters to enums without caring about upper/lower case,
 * so "last_name", "LAST_NAME" and "Last_Name" all become UserSortField.LAST_NAME.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class StringToEnumIgnoreCaseConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> {
            String value = source.trim();
            if (value.isEmpty()) {
                return null;
            }
            for (T constant : targetType.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Invalid value '" + source + "'. Allowed values: "
                    + Arrays.toString(targetType.getEnumConstants()));
        };
    }
}
