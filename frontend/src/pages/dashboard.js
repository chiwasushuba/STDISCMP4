import markup from "./dashboard.html?raw";
import { courseGrid, setEnrollmentCounts } from "../components/course-grid.js";
import { loadFeature } from "../components/feature-panel.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function loadDashboard(view, { user }) {
  view.replaceChildren(clone("dashboard"));
  setText(view, "[data-first-name]", user.firstName);
  return Promise.all([
    loadFeature(
      view.querySelector("[data-course-feature]"),
      "/api/courses",
      "Loading courses…",
      "Courses are taking a short break.",
      (courses) => {
        setEnrollmentCounts(
          view,
          courses.filter((course) => course.enrolled),
        );
        setText(
          view,
          "[data-open-count]",
          courses.filter((course) => course.open && course.seats > 0).length,
        );
        return courseGrid(courses.slice(0, 3));
      },
    ),
    loadFeature(
      view.querySelector("[data-grade-feature]"),
      "/api/grades",
      "Loading grades…",
      "Grades are temporarily unavailable.",
      (grades) => {
        const summary = clone("record-summary");
        setText(summary, "[data-grade-count]", grades.length);
        return summary;
      },
    ),
  ]);
}
