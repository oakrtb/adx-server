package com.oakrtb.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;

/** 让 Spring MVC 把 {@code application/x-protobuf} POST 读成 protobuf 消息。 */
@Configuration
public class OpenRtbConfig {

    @Bean
    public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        return new ProtobufHttpMessageConverter();
    }
}
