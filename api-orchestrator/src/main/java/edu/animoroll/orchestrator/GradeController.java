package edu.animoroll.orchestrator;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GradeController extends GradeApiGrpc.GradeApiImplBase {
  private final ManagedChannel channel;

  public GradeController(@Qualifier("gradeChannel") ManagedChannel channel) {
    this.channel = channel;
  }

  @Override
  public void listGrades(GradeQuery request, StreamObserver<GradeList> observer) {
    Rpc.reply(
        observer,
        () ->
            GradeApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .listGrades(request));
  }

  @Override
  public void listClasses(Empty request, StreamObserver<ClassList> observer) {
    Rpc.reply(
        observer,
        () ->
            GradeApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .listClasses(request));
  }

  @Override
  public void roster(RosterQuery request, StreamObserver<GradeList> observer) {
    Rpc.reply(
        observer,
        () ->
            GradeApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .roster(request));
  }

  @Override
  public void upload(GradeUpload request, StreamObserver<Receipt> observer) {
    Rpc.reply(
        observer,
        () ->
            GradeApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .upload(request));
  }
}
