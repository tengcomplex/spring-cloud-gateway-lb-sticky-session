package com.github.tengcomplex.gateway.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class StickySessionLoadBalancerConfiguration {
  public static final Logger L = LoggerFactory.getLogger(StickySessionLoadBalancerConfiguration.class);

  @Bean
  WebClient webClient() {
    return WebClient.builder().defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
  }

  /**
   * See
   * org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration#reactorServiceInstanceLoadBalancer(org.springframework.core.env.Environment,
   * org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory)
   */
  @Bean
  @Lazy
  public ReactorLoadBalancer<ServiceInstance> leastConn(Environment environment,
      LoadBalancerClientFactory loadBalancerClientFactory) {
    String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
    L.debug("name: {}", name);
    return new StickySessionLoadBalancer(loadBalancerClientFactory.getProvider(name, ServiceInstanceListSupplier.class),
        name);
  }
}
