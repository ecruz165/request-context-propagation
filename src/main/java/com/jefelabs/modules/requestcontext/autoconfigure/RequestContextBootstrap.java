package com.jefelabs.modules.requestcontext.autoconfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = {
        "com.jefelabs.modules.requestcontext.config",
        "com.jefelabs.modules.requestcontext.filter",
        "com.jefelabs.modules.requestcontext.logging",
        "com.jefelabs.modules.requestcontext.observability",
        "com.jefelabs.modules.requestcontext.service",
})
@Configuration
public class RequestContextBootstrap {
}
