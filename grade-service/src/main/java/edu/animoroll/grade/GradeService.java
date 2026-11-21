package edu.animoroll.grade;

import edu.animoroll.common.*;
import edu.animoroll.contracts.*;
import io.grpc.Status;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeService {
  private final JdbcTemplate jdbc;

  public GradeService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public GradeList grades(String term) {
    Actor student = Actor.require("STUDENT");
    Rpc.check(term.length() <= 40, "Invalid semester.");
    return query(
        "where r.student_id=? and r.value<>'' and (?='' or r.term=?)", student.id(), term, term);
  }

  public ClassList classes() {
    Actor faculty = Actor.require("FACULTY");
    return ClassList.newBuilder()
        .addAllClasses(
            jdbc.query(
                "select * from teaching_class where faculty_id=? order by term desc, course_code",
                (rs, index) ->
                    TeachingClass.newBuilder()
                        .setCourseCode(rs.getString("course_code"))
                        .setCourseTitle(rs.getString("course_title"))
                        .setTerm(rs.getString("term"))
                        .build(),
                faculty.id()))
        .build();
  }

  public GradeList roster(String code, String term) {
    requireAssignment(code, term);
    return query("where r.course_code=? and r.term=?", code, term);
  }

  @Transactional
  public Receipt upload(GradeUpload request) {
    requireAssignment(request.getCourseCode(), request.getTerm());
    Rpc.check(
        request.getRowsCount() > 0 && request.getRowsCount() <= 500,
        "Upload between 1 and 500 grade rows.");
    // Consistent lock order prevents overlapping uploads from deadlocking.
    Map<String, String> grades = new TreeMap<>();
    for (GradeRow row : request.getRowsList()) {
      Rpc.check(
          row.getStudentId().length() <= 40 && !grades.containsKey(row.getStudentId()),
          "Each student must appear once in the file.");
      grades.put(row.getStudentId(), normalizeGrade(row.getValue()));
    }
    for (var grade : grades.entrySet()) {
      int updated =
          jdbc.update(
              "update grade_record set value=? where student_id=? and course_code=? and term=?",
              grade.getValue(),
              grade.getKey(),
              request.getCourseCode(),
              request.getTerm());
      Rpc.check(
          updated == 1,
          "Student " + grade.getKey() + " is not in this class roster. No grades were saved.");
    }
    return Receipt.newBuilder()
        .setMessage(
            (grades.size() == 1 ? "1 grade" : grades.size() + " grades")
                + " saved. Review the updated roster below.")
        .build();
  }

  public static String normalizeGrade(String input) {
    String value = input.trim().toUpperCase(Locale.ROOT);
    Rpc.check(value.length() <= 8, "Invalid grade value.");
    if (Set.of("INC", "DRP").contains(value)) return value;
    try {
      BigDecimal number = new BigDecimal(value);
      boolean valid =
          number.compareTo(BigDecimal.ONE) >= 0
              && number.compareTo(new BigDecimal("3")) <= 0
              && number.remainder(new BigDecimal("0.25")).compareTo(BigDecimal.ZERO) == 0;
      valid |=
          number.compareTo(new BigDecimal("4")) == 0 || number.compareTo(new BigDecimal("5")) == 0;
      Rpc.check(valid, "Use 1.00–3.00 in steps of 0.25, 4.00, 5.00, INC, or DRP.");
      return number.setScale(2).toPlainString();
    } catch (NumberFormatException exception) {
      throw Status.INVALID_ARGUMENT
          .withDescription("Invalid grade. Use a numeric grade, INC, or DRP.")
          .asRuntimeException();
    }
  }

  private void requireAssignment(String code, String term) {
    Actor faculty = Actor.require("FACULTY");
    Rpc.check(code.length() <= 20 && term.length() <= 40, "Invalid class.");
    int count =
        jdbc.queryForObject(
            "select count(*) from teaching_class where course_code=? and term=? and faculty_id=?",
            Integer.class,
            code,
            term,
            faculty.id());
    if (count == 0)
      throw Status.PERMISSION_DENIED
          .withDescription("You may upload grades only for your assigned classes.")
          .asRuntimeException();
  }

  private GradeList query(String condition, Object... args) {
    return GradeList.newBuilder()
        .addAllGrades(
            jdbc.query(
                """
                select r.*, c.course_title, c.units from grade_record r
                join teaching_class c on c.course_code=r.course_code and c.term=r.term
                """
                    + condition
                    + " order by r.term desc, r.course_code, r.student_id",
                (rs, index) ->
                    Grade.newBuilder()
                        .setStudentId(rs.getString("student_id"))
                        .setStudentName(rs.getString("student_name"))
                        .setCourseCode(rs.getString("course_code"))
                        .setCourseTitle(rs.getString("course_title"))
                        .setTerm(rs.getString("term"))
                        .setValue(rs.getString("value"))
                        .setUnits(rs.getInt("units"))
                        .build(),
                args))
        .build();
  }
}
