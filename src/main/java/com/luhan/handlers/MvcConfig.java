package com.luhan.handlers;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ImportResource("classpath:/spring-mybatis.xml")
@ComponentScan(
		basePackages = 
	{"",""})//用于填写需要扫描的包
public class MvcConfig {
}
