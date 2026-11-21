import markup from "./enrollment.html?raw";
import { courseGrid, setEnrollmentCounts } from "../components/course-grid.js";
import { loadFeature } from "../components/feature-panel.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function loadEnrollment(view) {
  view.replaceChildren(clone("enrollment"));
  return loadFeature(
    view.querySelector("[data-course-feature]"),
    "/api/enrollment",
    "Loading your enrollment…",
    "Your enrollment is temporarily unavailable.",
    (courses) => {
      const results = clone("enrollment-results");
      setEnrollmentCounts(results, courses);
      results.querySelector(".course-grid").replaceWith(courseGrid(courses));
      results.querySelector(".empty-state").hidden = courses.length > 0;
      return results;
    },
  );
}
