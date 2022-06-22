package com.github.tengcomplex.gateway.configuration;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class ReactiveLoadBalancerStickySessionFilter implements GlobalFilter, Ordered {
  public static final Logger L = LoggerFactory.getLogger(ReactiveLoadBalancerStickySessionFilter.class);

  private static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10149;

  private LoadBalancerClientFactory clientFactory;

  public ReactiveLoadBalancerStickySessionFilter(LoadBalancerClientFactory clientFactory,
      LoadBalancerProperties properties) {
    this.clientFactory = clientFactory;
  }

  public ReactiveLoadBalancerStickySessionFilter() {

  }

  @Override
  public int getOrder() {
    return LOAD_BALANCER_CLIENT_FILTER_ORDER;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
    String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
    L.debug("Filtering, url: {}, schemePrefix: {}", url, schemePrefix);
    if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
      L.debug("Not choosing, go further in the chain");
      return chain.filter(exchange);
    }
    // preserve the original url
    addOriginalRequestUrl(exchange, url);
    L.trace("{} url before: {}", ReactiveLoadBalancerStickySessionFilter.class.getSimpleName(), url);

    return choose(exchange).doOnNext(response -> {

      if (!response.hasServer()) {
        throw NotFoundException.create(true, "Unable to find instance for " + url.getHost());
      }
      URI uri = exchange.getRequest().getURI();
      // if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
      // if the loadbalancer doesn't provide one.
      String overrideScheme = null;
      if (schemePrefix != null) {
        overrideScheme = url.getScheme();
      }
      DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(response.getServer(), overrideScheme);
      URI requestUrl = reconstructURI(serviceInstance, uri);
      L.debug("Url chosen: {}", requestUrl);
      exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
    }).then(chain.filter(exchange));
  }

  protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
    return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
  }

  private Mono<Response<ServiceInstance>> choose(ServerWebExchange exchange) {
    URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
    L.debug("We are choosing, uri: {}", uri);
    ReactorLoadBalancer<ServiceInstance> loadBalancer = this.clientFactory.getInstance(uri.getHost(),
        ReactorLoadBalancer.class, ServiceInstance.class);
    if (loadBalancer == null) {
      throw new NotFoundException("No loadbalancer available for " + uri.getHost());
    }
    L.debug("Using loadbalancer {}", loadBalancer.getClass().getSimpleName());
    Mono<Response<ServiceInstance>> ret = loadBalancer.choose(createRequest(exchange));
    ret.subscribe(r -> L.debug("We have {}", r.getServer().getUri()));
    return ret;
  }

  private Request<ServerWebExchange> createRequest(ServerWebExchange exchange) {
    return new DefaultRequest<ServerWebExchange>(exchange);
  }
}
