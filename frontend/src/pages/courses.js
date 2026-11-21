import markup from "./courses.html?raw";
import { courseGrid } from "../components/course-grid.js";
import { loadFeature } from "../components/feature-panel.js";
import { urlWithParams } from "../lib/api.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function loadCourses(view, { params }) {
  view.replaceChildren(clone("courses"));
  view.querySelector("#search").value = params.get("search") || "";
  view.querySelector("#department").value = params.get("department") || "";
  const query = {
    search: params.get("search") || "",
    department: params.get("department") || "",
  };
  return loadFeature(
    view.querySelector("[data-course-feature]"),
    urlWithParams("/api/courses", query),
    "Loading courses…",
    "We couldn’t load the catalog.",
    (courses) => {
      const results = clone("course-results");
      setText(results, "[data-course-count]", courses.length);
      results.querySelector(".course-grid").replaceWith(courseGrid(courses));
      results.querySelector(".empty-state").hidden = courses.length > 0;
      return results;
    },
  );
}
