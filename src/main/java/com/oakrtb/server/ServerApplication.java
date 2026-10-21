package com.oakrtb.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

@SpringBootApplication
public class ServerApplication {

    static {
        // OakRTB 用英文错误文案区分 required/type/format。中文默认语言会把这些码收成 constraint。
        Locale.setDefault(Locale.ENGLISH);
    }

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
