package com.example.cleancarsapi.config;

import com.example.cleancarsapi.entity.ServiceOrderStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets {@code ?status=} query params be sent in the DB's lowercase form
 * ({@code pending}, {@code in_progress}, ...) as well as the enum's own name.
 */
@Component
public class StringToServiceOrderStatusConverter implements Converter<String, ServiceOrderStatus> {

    @Override
    public ServiceOrderStatus convert(String source) {
        return ServiceOrderStatus.fromDb(source);
    }
}
