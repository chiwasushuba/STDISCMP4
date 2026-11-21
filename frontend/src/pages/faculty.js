import markup from "./faculty.html?raw";
import { loadFeature } from "../components/feature-panel.js";
import { urlWithParams } from "../lib/api.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function showFaculty(view, { params }) {
  view.replaceChildren(clone("faculty"));
  return loadFaculty(view, params);
}

function renderFaculty({ classes, selectedClass, roster, preview }) {
  const results = clone("faculty-results");
  const tabs = results.querySelector(".class-tabs");
  for (const assigned of classes) {
    const tab = clone("class-tab");
    const link = tab.querySelector("a");
    link.href = urlWithParams("/faculty", {
      code: assigned.courseCode,
      term: assigned.term,
    });
    setText(tab, "strong", assigned.courseCode);
    setText(tab, "span", assigned.term);
    if (
      assigned.courseCode === selectedClass?.courseCode &&
      assigned.term === selectedClass.term
    ) {
      link.classList.add("selected");
      link.setAttribute("aria-current", "page");
    }
    tabs.append(tab);
  }
  if (!selectedClass) {
    tabs.hidden = true;
    results.querySelector(".faculty-grid").hidden = true;
    results.querySelector(".empty-state").hidden = false;
    return results;
  }

  const { courseCode, courseTitle, term } = selectedClass;
  setText(results, "[data-course-code]", courseCode);
  setText(results, ".panel-header h2", courseTitle);
  setText(results, "[data-roster-count]", roster.length);
  results.querySelector("[data-template-download]").href = urlWithParams(
    "/api/faculty/template",
    { code: courseCode, term },
  );
  const upload = results.querySelector(".upload-form");
  upload.elements.code.value = courseCode;
  upload.elements.term.value = term;
  const file = upload.elements.file;
  file.addEventListener("change", () => {
    file.setCustomValidity(
      file.files[0]?.size > Number(file.dataset.maxBytes)
        ? "Choose a CSV file no larger than 256 KB."
        : "",
    );
    if (!file.validity.valid) file.reportValidity();
  });
  results.querySelector("tbody").replaceChildren(
    ...roster.map((grade) => {
      const row = clone("roster-row");
      setText(row, "strong", grade.studentName);
      setText(row, "[data-student-id]", grade.studentId);
      setText(row, "span", grade.value || "Not published");
      row.querySelector("span").className = grade.value
        ? "grade-pill"
        : "muted";
      return row;
    }),
  );
  if (preview) results.append(gradePreview(preview));
  return results;
}

function gradePreview(preview) {
  const content = clone("grade-preview");
  const count = preview.rows.length;
  setText(
    content,
    "[data-row-count]",
    `${count} grade ${count === 1 ? "row" : "rows"}`,
  );
  for (const input of content.querySelectorAll('input[name="previewId"]'))
    input.value = preview.id;
  content.querySelector("tbody").replaceChildren(
    ...preview.rows.map((grade) => {
      const row = clone("preview-row");
      setText(row, "[data-student-id]", grade.studentId);
      setText(row, "[data-grade]", grade.value);
      return row;
    }),
  );
  return content;
}

export function loadFaculty(view, params) {
  const query = {
    code: params.get("code") || "",
    term: params.get("term") || "",
  };
  return loadFeature(
    view.querySelector("[data-grade-feature]"),
    urlWithParams("/api/faculty", query),
    "Loading your classes…",
    "Your teaching workspace is temporarily unavailable.",
    renderFaculty,
  );
}
