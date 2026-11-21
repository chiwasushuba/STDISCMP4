import markup from "./grades.html?raw";
import { loadFeature } from "../components/feature-panel.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function loadGrades(view, { params }) {
  view.replaceChildren(clone("grades"));
  return loadFeature(
    view.querySelector("[data-grade-feature]"),
    "/api/grades",
    "Loading grades…",
    "We couldn’t load your grades.",
    (grades) => {
      const results = clone("grade-results");
      const selected = params.get("term") || "";
      const terms = new Set(grades.map((grade) => grade.term));
      const select = results.querySelector("#term");
      for (const term of terms)
        select.append(new Option(term, term, false, term === selected));
      const found = grades.filter(
        (grade) => !selected || grade.term === selected,
      );
      const rows = found.map((grade) => {
        const row = clone("grade-row");
        setText(row, "strong", grade.courseCode);
        setText(row, ".cell-sub", grade.courseTitle);
        setText(row, "[data-term]", grade.term);
        setText(row, "[data-units]", grade.units);
        setText(row, ".grade-pill", grade.value);
        return row;
      });
      results.querySelector("tbody").replaceChildren(...rows);
      results.querySelector(".empty-state").hidden = found.length > 0;
      return results;
    },
  );
}
