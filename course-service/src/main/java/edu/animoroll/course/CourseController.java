package edu.animoroll.course;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class CourseController extends CourseApiGrpc.CourseApiImplBase {
  private final CourseService service;

  public CourseController(CourseService service) {
    this.service = service;
  }

  @Override
  public void listCourses(CourseQuery request, StreamObserver<CourseList> observer) {
    Rpc.reply(observer, () -> service.list(request));
  }

  @Override
  public void enroll(EnrollRequest request, StreamObserver<Receipt> observer) {
    Rpc.reply(observer, () -> service.enroll(request.getCourseId()));
  }
}
