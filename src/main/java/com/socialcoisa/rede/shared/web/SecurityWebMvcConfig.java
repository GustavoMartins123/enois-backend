package com.socialcoisa.rede.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra as travas de autorização que valem por caminho, não por controller. */
@Configuration
class SecurityWebMvcConfig implements WebMvcConfigurer {

	private final AdminRouteInterceptor adminRouteInterceptor;

	SecurityWebMvcConfig(AdminRouteInterceptor adminRouteInterceptor) {
		this.adminRouteInterceptor = adminRouteInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(adminRouteInterceptor).addPathPatterns("/admin/**");
	}
}
