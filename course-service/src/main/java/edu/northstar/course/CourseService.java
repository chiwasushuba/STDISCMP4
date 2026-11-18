package edu.northstar.course;

import edu.northstar.common.*;
import edu.northstar.contracts.*;
import io.grpc.Status;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
  private final JdbcTemplate jdbc;

  public CourseService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CourseList list(CourseQuery query) {
    Actor actor = Actor.require("STUDENT");
    Rpc.check(
        query.getSearch().length() <= 100 && query.getDepartment().length() <= 80,
        "Search is too long.");
    String search = "%" + query.getSearch().trim().toLowerCase(java.util.Locale.ROOT) + "%";
    List<Course> courses =
        jdbc.query(
            """
                select c.*, c.capacity-(select count(*) from enrollment e where e.course_id=c.id) as seats,
                exists(select 1 from enrollment e where e.course_id=c.id and e.student_id=?) as enrolled
                from course c where (lower(c.code) like ? or lower(c.title) like ?)
                and (?='' or c.department=?) order by c.code
                """,
            (rs, index) ->
                Course.newBuilder()
                    .setId(rs.getString("id"))
                    .setCode(rs.getString("code"))
                    .setTitle(rs.getString("title"))
                    .setDepartment(rs.getString("department"))
                    .setUnits(rs.getInt("units"))
                    .setSchedule(rs.getString("schedule"))
                    .setRoom(rs.getString("room"))
                    .setFaculty(rs.getString("faculty"))
                    .setCapacity(rs.getInt("capacity"))
                    .setSeats(rs.getInt("seats"))
                    .setOpen(rs.getBoolean("open"))
                    .setEnrolled(rs.getBoolean("enrolled"))
                    .build(),
            actor.id(),
            search,
            search,
            query.getDepartment(),
            query.getDepartment());
    return CourseList.newBuilder()
        .addAllCourses(
            courses.stream().filter(c -> !query.getEnrolledOnly() || c.getEnrolled()).toList())
        .build();
  }

  @Transactional
  public Receipt enroll(String courseId) {
    Actor actor = Actor.require("STUDENT");
    Rpc.check(courseId.length() <= 40, "Invalid course.");
    var courses = jdbc.queryForList("select * from course where id=? for update", courseId);
    if (courses.isEmpty())
      throw Status.NOT_FOUND.withDescription("This course was not found.").asRuntimeException();
    // A per-course lock serializes seat allocation across all service replicas.
    if (jdbc.queryForObject(
            "select count(*) from enrollment where student_id=? and course_id=?",
            Integer.class,
            actor.id(),
            courseId)
        > 0) {
      return Receipt.newBuilder().setMessage("You are already enrolled in this course.").build();
    }
    var course = courses.getFirst();
    Rpc.check((Boolean) course.get("open"), "Enrollment is closed for this course.");
    int count =
        jdbc.queryForObject(
            "select count(*) from enrollment where course_id=?", Integer.class, courseId);
    Rpc.check(
        count < ((Number) course.get("capacity")).intValue(),
        "This course is full. Please choose another course.");
    jdbc.update(
        "insert into enrollment(student_id, course_id) values (?, ?)", actor.id(), courseId);
    return Receipt.newBuilder()
        .setMessage(
            "Enrollment confirmed for " + course.get("code") + ". View it in My enrollment.")
        .build();
  }
}
