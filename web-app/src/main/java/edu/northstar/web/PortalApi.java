package edu.northstar.web;

import edu.northstar.common.Rpc;
import edu.northstar.contracts.*;
import io.grpc.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
public class PortalApi {
  private final ManagedChannel channel;

  public PortalApi(ManagedChannel channel) {
    this.channel = channel;
  }

  public AuthReply login(String email, String password) {
    return IdentityApiGrpc.newBlockingStub(channel)
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .login(LoginRequest.newBuilder().setEmail(email).setPassword(password).build());
  }

  private Channel authenticated(PortalSession session) {
    synchronized (session) {
      if (session.needsRefresh()) {
        session.update(
            IdentityApiGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .refresh(
                    RefreshRequest.newBuilder().setRefreshToken(session.refreshToken()).build()));
      }
      return Rpc.authenticated(channel, session.accessToken());
    }
  }

  public void logout(PortalSession session) {
    IdentityApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .logout(Empty.getDefaultInstance());
  }

  public CourseList courses(
      PortalSession session, String search, String department, boolean enrolledOnly) {
    return CourseApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .listCourses(
            CourseQuery.newBuilder()
                .setSearch(search)
                .setDepartment(department)
                .setEnrolledOnly(enrolledOnly)
                .build());
  }

  public Receipt enroll(PortalSession session, String course) {
    return CourseApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .enroll(EnrollRequest.newBuilder().setCourseId(course).build());
  }

  public GradeList grades(PortalSession session, String term) {
    return GradeApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .listGrades(GradeQuery.newBuilder().setTerm(term).build());
  }

  public ClassList classes(PortalSession session) {
    return GradeApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .listClasses(Empty.getDefaultInstance());
  }

  public GradeList roster(PortalSession session, String code, String term) {
    return GradeApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .roster(RosterQuery.newBuilder().setCourseCode(code).setTerm(term).build());
  }

  public Receipt upload(PortalSession session, GradeUpload request) {
    return GradeApiGrpc.newBlockingStub(authenticated(session))
        .withDeadlineAfter(3, TimeUnit.SECONDS)
        .upload(request);
  }
}

@Configuration
class PortalChannelConfiguration {
  @Bean(destroyMethod = "shutdown")
  ManagedChannel orchestratorChannel(@Value("${rpc.orchestrator.target}") String target) {
    return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
  }
}
