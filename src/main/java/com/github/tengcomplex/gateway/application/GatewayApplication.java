package com.github.tengcomplex.gateway.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.github.tengcomplex.gateway.configuration.ReactiveLoadBalancerStickySessionFilter;


@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan({"com.github.tengcomplex.gateway.configuration"})
@LoadBalancerClients({
  @LoadBalancerClient(value = "frontend-service", configuration = com.github.tengcomplex.gateway.configuration.StickySessionLoadBalancerConfiguration.class)})
public class GatewayApplication {

  @Value("com.github.tengcomplex.gateway.frontendUri:lb://frontend-service")
  private String frontendUri;

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("frontend",
            r -> r.path("/**")
                .uri(frontendUri))
        .build();
  }

  public static void main(String[] args) {
    new SpringApplicationBuilder(GatewayApplication.class).run(args);
  }

  /**
   * Bean for a sticky session loadbalancer.<br>
   *
   * Siehe: https://github.com/spring-cloud/spring-cloud-commons/pull/764
   * @param clientFactory
   * @param properties
   * @return
   */
  @Bean
  public GlobalFilter customFilter(LoadBalancerClientFactory clientFactory, LoadBalancerProperties properties) {
    return new ReactiveLoadBalancerStickySessionFilter(clientFactory, properties);
  }

}
