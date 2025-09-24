package com.jefelabs.modules.requestcontext.demo;

import com.jefelabs.modules.requestcontext.annotation.EnableRequestContextV2;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties;
import com.jefelabs.modules.requestcontext.demo.config.TestWebClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableRequestContextV2
@EnableConfigurationProperties(RequestContextProperties.class)
@Import(TestWebClientConfig.class)
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

