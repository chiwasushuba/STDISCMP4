package edu.northstar.web;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CsvGradesTest {
  @Test
  void parsesBomAndWindowsLineEndings() {
    var upload =
        CsvGrades.parse(
            "\uFEFFstudent_id,grade\r\n2024-001,1.25\r\n2024-002,INC\r\n", "CS 301", "term");
    assertThat(upload.getRowsCount()).isEqualTo(2);
    assertThat(upload.getRows(1).getValue()).isEqualTo("INC");
  }

  @Test
  void rejectsWrongHeadersDuplicatesAndExtraColumns() {
    for (String input :
        new String[] {
          "student,grade\n2024-001,1",
          "student_id,grade\n2024-001,1\n2024-001,2",
          "student_id,grade\n2024-001,1,2",
          "student_id,grade\n2024-001,"
        }) {
      assertThatThrownBy(() -> CsvGrades.parse(input, "CS 301", "term"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
