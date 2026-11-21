package edu.animoroll.grade;

import edu.animoroll.common.Rpc;
import edu.animoroll.contracts.*;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class GradeController extends GradeApiGrpc.GradeApiImplBase {
  private final GradeService service;

  public GradeController(GradeService service) {
    this.service = service;
  }

  @Override
  public void listGrades(GradeQuery request, StreamObserver<GradeList> observer) {
    Rpc.reply(observer, () -> service.grades(request.getTerm()));
  }

  @Override
  public void listClasses(Empty request, StreamObserver<ClassList> observer) {
    Rpc.reply(observer, service::classes);
  }

  @Override
  public void roster(RosterQuery request, StreamObserver<GradeList> observer) {
    Rpc.reply(observer, () -> service.roster(request.getCourseCode(), request.getTerm()));
  }

  @Override
  public void upload(GradeUpload request, StreamObserver<Receipt> observer) {
    Rpc.reply(observer, () -> service.upload(request));
  }
}
