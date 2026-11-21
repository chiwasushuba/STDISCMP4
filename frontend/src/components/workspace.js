import markup from "./workspace.html?raw";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function renderWorkspace(session, path, title, breadcrumb) {
  const { user, currentTerm } = session;
  const faculty = user.role === "FACULTY";
  const workspace = clone("workspace");
  setText(
    workspace,
    ".workspace-label",
    faculty ? "FACULTY WORKSPACE" : "STUDENT WORKSPACE",
  );
  setText(workspace, ".profile .avatar", user.initials);
  setText(workspace, ".profile strong", user.name);
  setText(workspace, ".profile div span", user.id);
  setText(workspace, "[data-breadcrumb]", breadcrumb);
  setText(workspace, "[data-current-term]", currentTerm);
  setText(workspace, "h1", title);
  for (const link of workspace.querySelectorAll(".main-nav a")) {
    if ((link.pathname === "/faculty") !== faculty) {
      link.remove();
    } else if (link.pathname === path) {
      link.classList.add("active");
      link.setAttribute("aria-current", "page");
    }
  }
  return workspace;
}
