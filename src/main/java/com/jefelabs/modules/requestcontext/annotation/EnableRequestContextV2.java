package com.jefelabs.modules.requestcontext.annotation;


import com.jefelabs.modules.requestcontext.autoconfigure.RequestContextBootstrap;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@Import(RequestContextBootstrap.class)
public @interface EnableRequestContextV2 {
}
