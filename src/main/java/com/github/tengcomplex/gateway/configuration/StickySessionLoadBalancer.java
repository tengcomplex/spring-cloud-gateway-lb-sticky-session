package com.github.tengcomplex.gateway.configuration;


import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * A session cookie based implementation of {@link ReactorServiceInstanceLoadBalancer} that ensures requests from the
 * same client are routed to the same server.
 *
 * @author Andrew Fitzgerald
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 */
public class StickySessionLoadBalancer implements ReactorServiceInstanceLoadBalancer {
  public static final Logger L = LoggerFactory.getLogger(StickySessionLoadBalancer.class);

  private final ReactorServiceInstanceLoadBalancer delegate;
  private final String serviceId;
  // TODO make this configurable
  private final String cookieName = "scg-instance-id";

  private ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

  /**
   * @param serviceInstanceListSupplierProvider a provider of {@link ServiceInstanceListSupplier} that will be used to
   *                                            get available instances
   * @param serviceId                           id of the service for which to choose an instance
   */
  public StickySessionLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
      String serviceId) {
    this(serviceInstanceListSupplierProvider, serviceId,
        new RoundRobinLoadBalancer(serviceInstanceListSupplierProvider, serviceId));
  }

  /**
   * @param serviceInstanceListSupplierProvider a provider of {@link ServiceInstanceListSupplier} that will be used to
   *                                            get available instances
   * @param serviceId                           id of the service for which to choose an instance
   * @param delegate                            The delegate load balancer to use if the incoming request does not
   *                                            already have a valid instance selected
   */
  public StickySessionLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
      String serviceId, ReactorServiceInstanceLoadBalancer delegate) {
    this.serviceId = serviceId;
    this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    this.delegate = delegate;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Mono<Response<ServiceInstance>> choose(Request request) {
    ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
        .getIfAvailable(NoopServiceInstanceListSupplier::new);
    return supplier.get().next().flatMap(list -> getInstanceResponse(list, request));
  }

  @SuppressWarnings("rawtypes")
  private Mono<Response<ServiceInstance>> getInstanceResponse(List<ServiceInstance> instances, Request request) {
    if (instances.isEmpty()) {
      L.warn("No servers available for service: {}", this.serviceId);
      return Mono.just(new EmptyResponse());
    }
    L.debug("request: {}, instances: {}", request.getClass().getSimpleName(), instances);
    Object context = request.getContext();
    L.debug("Context class name: {}", context.getClass().getSimpleName());
    if (!(context instanceof ServerWebExchange)) {
      throw new IllegalArgumentException("The context must be a ServerWebExchange");
    }
    ServerWebExchange exchange = (ServerWebExchange) context;
    L.debug("exchange: {}", exchange);
    // Check if the exchange has a cookie that points to a valid ServiceInstance
    return serviceInstanceFromCookie(exchange, instances)
        // if it does, then route to that server
        .map(instance -> Mono.just((Response<ServiceInstance>) new DefaultResponse(instance)))
        // otherwise we'll let the delegate pick a server
        .orElseGet(() -> delegate.choose(request))
        // either way we should set/renew the cookie
        .doOnNext(response -> setCookie(exchange, response));
  }

  private Optional<ServiceInstance> serviceInstanceFromCookie(ServerWebExchange exchange,
      List<ServiceInstance> instances) {
    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
    if (cookie == null) {
      return Optional.empty();
    }
    String cookieInstanceId = cookie.getValue();
    return instances.stream().filter(instance -> Objects.equals(instance.getInstanceId(), cookieInstanceId))
        .findFirst();
  }

  private void setCookie(ServerWebExchange exchange, Response<ServiceInstance> response) {
    if (!response.hasServer()) {
      return;
    }
    // Same cookie mit empty path for all exchanges
    ResponseCookie responseCookie = ResponseCookie.from(cookieName, response.getServer().getInstanceId()).path("/").build();
    L.debug("ResponseCookie: {}", responseCookie);
    exchange.getResponse().addCookie(responseCookie);
  }

}

