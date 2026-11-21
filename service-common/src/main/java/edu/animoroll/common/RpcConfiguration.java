package edu.animoroll.common;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
public class RpcConfiguration {
  @Bean
  JwtDecoder jwtDecoder(@Value("${security.public-key}") Resource key) throws Exception {
    return Tokens.decoder(Tokens.publicKey(key));
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnProperty("rpc.server.port")
  Server rpcServer(
      @Value("${rpc.server.port}") int port, List<BindableService> services, JwtDecoder decoder) {
    NettyServerBuilder builder = NettyServerBuilder.forPort(port).maxInboundMessageSize(512 * 1024);
    ServerInterceptor security =
        new ServerInterceptor() {
          @Override
          public <Q, S> ServerCall.Listener<Q> interceptCall(
              ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
            String method = call.getMethodDescriptor().getFullMethodName();
            if (method.equals("animoroll.v1.IdentityApi/Login")
                || method.equals("animoroll.v1.IdentityApi/Refresh")) {
              return next.startCall(call, headers);
            }
            try {
              String auth = headers.get(Rpc.AUTH);
              if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException();
              Actor actor = Tokens.actor(decoder, auth.substring(7));
              return Contexts.interceptCall(
                  Context.current().withValue(Actor.CURRENT, actor), call, headers, next);
            } catch (Exception exception) {
              call.close(
                  Status.UNAUTHENTICATED.withDescription(
                      "Your session has expired. Please sign in again."),
                  new Metadata());
              return new ServerCall.Listener<>() {};
            }
          }
        };
    services.forEach(
        service -> builder.addService(ServerInterceptors.intercept(service, security)));
    return builder.build();
  }

  @Bean
  @ConditionalOnProperty("rpc.server.port")
  ApplicationListener<ApplicationReadyEvent> startRpcServer(Server server) {
    // Demo accounts and other startup tasks must finish before the RPC port accepts traffic.
    return event -> {
      try {
        server.start();
      } catch (IOException exception) {
        throw new IllegalStateException("Could not start the gRPC server.", exception);
      }
    };
  }
}
