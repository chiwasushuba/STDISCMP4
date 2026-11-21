package edu.animoroll.identity;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class IdentityController extends IdentityApiGrpc.IdentityApiImplBase {
  private final IdentityService service;

  public IdentityController(IdentityService service) {
    this.service = service;
  }

  @Override
  public void login(LoginRequest request, StreamObserver<AuthReply> observer) {
    Rpc.reply(observer, () -> service.login(request));
  }

  @Override
  public void refresh(RefreshRequest request, StreamObserver<AuthReply> observer) {
    Rpc.reply(observer, () -> service.refresh(request.getRefreshToken()));
  }

  @Override
  public void logout(Empty request, StreamObserver<Receipt> observer) {
    Rpc.reply(observer, service::logout);
  }
}
