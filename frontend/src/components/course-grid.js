import markup from "./course-grid.html?raw";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function courseGrid(courses) {
  const grid = document.createElement("div");
  grid.className = "course-grid";
  grid.replaceChildren(...courses.map(courseCard));
  return grid;
}

function courseCard(course) {
  const card = clone("course-card");
  setText(card, ".course-code", course.code);
  setText(card, "h3", course.title);
  setText(card, "[data-department]", course.department);
  setText(card, "[data-units]", course.units);
  setText(card, "[data-schedule]", course.schedule);
  setText(card, "[data-room]", course.room);
  setText(card, "[data-faculty]", course.faculty);
  setText(card, "[data-seats-left] strong", course.seats);
  card.querySelector("[data-seats-left]").hidden =
    !course.open || course.enrolled;
  card.querySelector(".confirmed").hidden = !course.enrolled;
  card.querySelector("[data-closed]").hidden = course.open || course.enrolled;

  const tag = card.querySelector(".tag");
  if (course.enrolled) {
    tag.textContent = "ENROLLED";
    tag.classList.add("mint");
  } else if (!course.open || course.seats === 0) {
    tag.textContent = course.open ? "FULL" : "CLOSED";
    tag.classList.add("gray");
  } else {
    tag.textContent = "OPEN";
    const form = card.querySelector("form");
    form.hidden = false;
    form.elements.courseId.value = course.id;
    form
      .querySelector("button")
      .setAttribute("aria-label", `Enroll in ${course.title}`);
  }
  return card;
}

export function setEnrollmentCounts(root, enrolled) {
  setText(root, "[data-enrolled-count]", enrolled.length);
  setText(
    root,
    "[data-unit-count]",
    enrolled.reduce((sum, course) => sum + course.units, 0),
  );
}
