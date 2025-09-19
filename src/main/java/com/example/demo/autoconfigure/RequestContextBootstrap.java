package com.example.demo.autoconfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = {
        "com.example.demo.config",
        "com.example.demo.filter",
        "com.example.demo.logging",
        "com.example.demo.observability",
        "com.example.demo.service",
})
@Configuration
public class RequestContextBootstrap {
}
