package edu.animoroll.course;

import static org.assertj.core.api.Assertions.*;

import edu.animoroll.common.Actor;
import edu.animoroll.contracts.*;
import io.grpc.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CourseServiceTest extends DatabaseTest {
  @Autowired CourseService service;
  @Autowired JdbcTemplate jdbc;

  private Context student(String id) {
    return Context.current()
        .withValue(Actor.CURRENT, new Actor(id, "Student", "STUDENT", "test", "token"));
  }

  @BeforeEach
  void reset() {
    jdbc.update("delete from enrollment");
    jdbc.update("update course set capacity=30, open=true where id='CS301'");
  }

  @Test
  void duplicateSubmissionIsIdempotent() throws Exception {
    student("A").call(() -> service.enroll("CS301"));
    student("A").call(() -> service.enroll("CS301"));
    assertThat(jdbc.queryForObject("select count(*) from enrollment", Integer.class)).isEqualTo(1);
    var result =
        student("A")
            .call(() -> service.list(CourseQuery.newBuilder().setEnrolledOnly(true).build()));
    assertThat(result.getCoursesList()).extracting(Course::getId).containsExactly("CS301");
  }

  @Test
  void concurrentStudentsCannotOverbookTheLastSeat() throws Exception {
    jdbc.update("update course set capacity=1 where id='CS301'");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of("A", "B").stream()
              .map(
                  id ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            start.await();
                            try {
                              student(id).call(() -> service.enroll("CS301"));
                              return true;
                            } catch (StatusRuntimeException exception) {
                              assertThat(exception.getStatus().getCode())
                                  .isEqualTo(Status.Code.INVALID_ARGUMENT);
                              return false;
                            }
                          }))
              .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      int successes = 0;
      for (var task : tasks) if (task.get(10, TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("select count(*) from enrollment", Integer.class)).isEqualTo(1);
  }

  @Test
  void closedCourseAndFacultyEnrollmentAreRejected() {
    assertThatThrownBy(() -> student("A").call(() -> service.enroll("CS304")))
        .isInstanceOf(StatusRuntimeException.class);
    var faculty =
        Context.current()
            .withValue(Actor.CURRENT, new Actor("F", "Faculty", "FACULTY", "test", "token"));
    assertThatThrownBy(() -> faculty.call(() -> service.enroll("CS301")))
        .isInstanceOf(StatusRuntimeException.class);
    assertThat(jdbc.queryForObject("select count(*) from enrollment", Integer.class)).isZero();
  }

  @Test
  void searchAndStudentOwnershipAreApplied() throws Exception {
    student("A").call(() -> service.enroll("CS301"));
    var result =
        student("B")
            .call(() -> service.list(CourseQuery.newBuilder().setSearch("distributed").build()));
    assertThat(result.getCoursesCount()).isEqualTo(1);
    assertThat(result.getCourses(0).getEnrolled()).isFalse();
    assertThat(result.getCourses(0).getSeats()).isEqualTo(29);
  }

  @Test
  void catalogSearchTreatsSpecialCharactersAsLiteralText() throws Exception {
    for (String search : List.of("%", "_", "\\")) {
      var result =
          student("A").call(() -> service.list(CourseQuery.newBuilder().setSearch(search).build()));
      assertThat(result.getCoursesList()).as("search for %s", search).isEmpty();
    }
  }
}
