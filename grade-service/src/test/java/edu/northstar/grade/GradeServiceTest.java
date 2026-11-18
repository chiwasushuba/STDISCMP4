package edu.northstar.grade;

import static org.assertj.core.api.Assertions.*;

import edu.northstar.common.Actor;
import edu.northstar.contracts.*;
import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class GradeServiceTest extends DatabaseTest {
  @Autowired GradeService service;
  @Autowired JdbcTemplate jdbc;
  private static final String TERM = "2026–2027 · Semester 1";

  private Context actor(String id, String role) {
    return Context.current().withValue(Actor.CURRENT, new Actor(id, "Test", role, "test", "token"));
  }

  private GradeUpload upload(String secondId) {
    return GradeUpload.newBuilder()
        .setCourseCode("CS 301")
        .setTerm(TERM)
        .addRows(GradeRow.newBuilder().setStudentId("2024-001").setValue("1.25"))
        .addRows(GradeRow.newBuilder().setStudentId(secondId).setValue("1.50"))
        .build();
  }

  @BeforeEach
  void reset() {
    jdbc.update("update grade_record set value='' where course_code='CS 301'");
  }

  @Test
  void invalidRosterRowRollsBackEntireUpload() {
    assertThatThrownBy(
            () -> actor("FAC-001", "FACULTY").call(() -> service.upload(upload("UNKNOWN"))))
        .isInstanceOf(StatusRuntimeException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from grade_record where course_code='CS 301' and value<>''",
                Integer.class))
        .isZero();
  }

  @Test
  void publishedGradesAreVisibleOnlyToTheirStudent() throws Exception {
    actor("FAC-001", "FACULTY").call(() -> service.upload(upload("2024-002")));
    var grades = actor("2024-001", "STUDENT").call(() -> service.grades(TERM));
    assertThat(grades.getGradesCount()).isEqualTo(1);
    assertThat(grades.getGrades(0).getValue()).isEqualTo("1.25");
    assertThat(grades.getGradesList()).allMatch(g -> g.getStudentId().equals("2024-001"));
  }

  @Test
  void facultyCannotChangeOtherFacultysClassAndStudentCannotUpload() {
    assertThatThrownBy(
            () -> actor("FAC-002", "FACULTY").call(() -> service.upload(upload("2024-002"))))
        .isInstanceOf(StatusRuntimeException.class);
    assertThatThrownBy(
            () -> actor("2024-001", "STUDENT").call(() -> service.upload(upload("2024-002"))))
        .isInstanceOf(StatusRuntimeException.class);
  }

  @Test
  void invalidAndDuplicateGradesAreRejectedAtomically() {
    assertThatThrownBy(() -> GradeService.normalizeGrade("NaN"))
        .isInstanceOf(StatusRuntimeException.class);
    assertThatThrownBy(() -> GradeService.normalizeGrade("2.13"))
        .isInstanceOf(StatusRuntimeException.class);
    assertThatThrownBy(() -> GradeService.normalizeGrade("3.25"))
        .isInstanceOf(StatusRuntimeException.class);
    assertThat(GradeService.normalizeGrade("1")).isEqualTo("1.00");
    assertThat(GradeService.normalizeGrade("inc")).isEqualTo("INC");
    assertThatThrownBy(
            () -> actor("FAC-001", "FACULTY").call(() -> service.upload(upload("2024-001"))))
        .isInstanceOf(StatusRuntimeException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from grade_record where course_code='CS 301' and value<>''",
                Integer.class))
        .isZero();
  }
}
