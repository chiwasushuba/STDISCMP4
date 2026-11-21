package edu.animoroll.orchestrator;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CourseController extends CourseApiGrpc.CourseApiImplBase {
  private final ManagedChannel channel;

  public CourseController(@Qualifier("courseChannel") ManagedChannel channel) {
    this.channel = channel;
  }

  @Override
  public void listCourses(CourseQuery request, StreamObserver<CourseList> observer) {
    Rpc.reply(
        observer,
        () ->
            CourseApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .listCourses(request));
  }

  @Override
  public void enroll(EnrollRequest request, StreamObserver<Receipt> observer) {
    Rpc.reply(
        observer,
        () ->
            CourseApiGrpc.newBlockingStub(Rpc.forwarded(channel))
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .enroll(request));
  }
}
