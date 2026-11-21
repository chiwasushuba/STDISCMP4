package edu.animoroll.web;

import edu.animoroll.contracts.*;
import java.util.*;

public final class CsvGrades {
  private CsvGrades() {}

  public static GradeUpload parse(String text, String code, String term) {
    String[] lines = text.replace("\uFEFF", "").strip().split("\\R");
    if (lines.length < 2 || lines.length > 501 || !lines[0].strip().equals("student_id,grade")) {
      throw new IllegalArgumentException("Use the header student_id,grade followed by 1–500 rows.");
    }
    var request = GradeUpload.newBuilder().setCourseCode(code).setTerm(term);
    Set<String> students = new HashSet<>();
    for (int i = 1; i < lines.length; i++) {
      String[] fields = lines[i].split(",", -1);
      if (fields.length != 2
          || !fields[0].strip().matches("[A-Za-z0-9-]{1,40}")
          || fields[1].isBlank()
          || fields[1].length() > 8) {
        throw new IllegalArgumentException(
            "Check row " + (i + 1) + ". Use a student ID and grade separated by one comma.");
      }
      if (!students.add(fields[0].strip()))
        throw new IllegalArgumentException("Duplicate student at row " + (i + 1) + ".");
      request.addRows(
          GradeRow.newBuilder().setStudentId(fields[0].strip()).setValue(fields[1].strip()));
    }
    return request.build();
  }
}
