package com.jefelabs.modules.requestcontext.autoconfigure;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RequestContextBootstrap.class)
public class RequestContextAutoConfig {
}
