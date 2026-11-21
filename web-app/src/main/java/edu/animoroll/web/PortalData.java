package edu.animoroll.web;

import edu.animoroll.contracts.Course;
import edu.animoroll.contracts.Grade;
import edu.animoroll.contracts.TeachingClass;
import java.util.List;

final class PortalData {
  private PortalData() {}

  record Session(User user, String currentTerm, Csrf csrf) {}

  record Csrf(String headerName, String token) {}

  record User(String id, String name, String role, String firstName, String initials) {
    static User from(PortalSession user) {
      return new User(
          user.getId(), user.getName(), user.getRole(), user.getFirstName(), user.getInitials());
    }
  }

  record Message(String message) {}

  record Signout(boolean revocationPending) {}

  record CourseItem(
      String id,
      String code,
      String title,
      String department,
      int units,
      String schedule,
      String room,
      String faculty,
      int capacity,
      int seats,
      boolean open,
      boolean enrolled) {
    static CourseItem from(Course course) {
      return new CourseItem(
          course.getId(),
          course.getCode(),
          course.getTitle(),
          course.getDepartment(),
          course.getUnits(),
          course.getSchedule(),
          course.getRoom(),
          course.getFaculty(),
          course.getCapacity(),
          course.getSeats(),
          course.getOpen(),
          course.getEnrolled());
    }
  }

  record GradeItem(
      String studentId,
      String studentName,
      String courseCode,
      String courseTitle,
      String term,
      String value,
      int units) {
    static GradeItem from(Grade grade) {
      return new GradeItem(
          grade.getStudentId(),
          grade.getStudentName(),
          grade.getCourseCode(),
          grade.getCourseTitle(),
          grade.getTerm(),
          grade.getValue(),
          grade.getUnits());
    }
  }

  record ClassItem(String courseCode, String courseTitle, String term) {
    static ClassItem from(TeachingClass item) {
      return new ClassItem(item.getCourseCode(), item.getCourseTitle(), item.getTerm());
    }
  }

  record Faculty(
      List<ClassItem> classes, ClassItem selectedClass, List<GradeItem> roster, Preview preview) {}

  record Preview(String id, List<PreviewRow> rows) {}

  record PreviewRow(String studentId, String value) {}
}
