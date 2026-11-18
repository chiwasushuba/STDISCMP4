package edu.northstar.common;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Rpc {
  private static final Logger LOG = LoggerFactory.getLogger(Rpc.class);
  public static final Metadata.Key<String> AUTH =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  private Rpc() {}

  public static Channel authenticated(Channel channel, String token) {
    Metadata metadata = new Metadata();
    metadata.put(AUTH, "Bearer " + token);
    return ClientInterceptors.intercept(
        channel, MetadataUtils.newAttachHeadersInterceptor(metadata));
  }

  public static Channel forwarded(Channel channel) {
    return authenticated(channel, Actor.current().token());
  }

  public static <T> void reply(StreamObserver<T> observer, Supplier<T> operation) {
    try {
      observer.onNext(operation.get());
      observer.onCompleted();
    } catch (StatusRuntimeException exception) {
      observer.onError(exception);
    } catch (Exception exception) {
      // Do not expose database errors, SQL, or configuration in client responses.
      LOG.error("RPC operation failed: {}", exception.getClass().getSimpleName());
      observer.onError(
          Status.UNAVAILABLE
              .withDescription("This feature is temporarily unavailable. Please try again.")
              .asRuntimeException());
    }
  }

  public static void check(boolean condition, String message) {
    if (!condition) throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
  }
}
