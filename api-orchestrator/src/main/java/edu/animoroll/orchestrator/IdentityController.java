package edu.animoroll.orchestrator;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class IdentityController extends IdentityApiGrpc.IdentityApiImplBase {
  private final ManagedChannel channel;

  public IdentityController(@Qualifier("identityChannel") ManagedChannel channel) {
    this.channel = channel;
  }

  @Override
  public void login(LoginRequest request, StreamObserver<AuthReply> observer) {
    Rpc.reply(
        observer,
        () ->
            IdentityApiGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .login(request));
  }

  @Override
  public void refresh(RefreshRequest request, StreamObserver<AuthReply> observer) {
    Rpc.reply(
        observer,
        () ->
            IdentityApiGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .refresh(request));
  }

  @Override
  public void logout(Empty request, StreamObserver<Receipt> observer) {
    Rpc.reply(
        observer,
        () ->
            IdentityApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .logout(request));
  }
}
