import "./styles/index.css";
import { renderFeatureError, setNotice } from "./components/feature-panel.js";
import { renderWorkspace } from "./components/workspace.js";
import { post, request } from "./lib/api.js";
import { submitForm } from "./lib/forms.js";
import { loadCourses } from "./pages/courses.js";
import { loadDashboard } from "./pages/dashboard.js";
import { loadEnrollment } from "./pages/enrollment.js";
import { loadFaculty, showFaculty } from "./pages/faculty.js";
import { loadGrades } from "./pages/grades.js";
import { showLogin } from "./pages/login.js";

const app = document.querySelector("#app");
const routes = {
  "/": {
    title: "Semester overview",
    breadcrumb: "Overview",
    render: loadDashboard,
  },
  "/courses": { title: "Course catalog", render: loadCourses },
  "/enrollment": { title: "My enrollment", render: loadEnrollment },
  "/grades": { title: "Grade history", render: loadGrades },
  "/faculty": { title: "Classes & grades", render: showFaculty },
};

async function workspaceAction(form, session, view) {
  const action = form.getAttribute("action");
  setNotice(app, "error", "");
  try {
    const result = await post(form, session.csrf);
    if (!form.isConnected) return;
    if (action === "/api/signout") {
      const suffix = result.revocationPending ? "&revocationPending" : "";
      location.assign(`/login?signedout${suffix}`);
    } else if (action === "/api/enroll") {
      history.pushState(null, "", "/enrollment");
      await showWorkspace(session, "/enrollment", true);
      setNotice(app, "success", result.message);
    } else {
      await loadFaculty(view, new URLSearchParams(location.search));
      if (result?.message) {
        setNotice(app, "success", result.message);
      } else if (action === "/api/faculty/preview") {
        view.querySelector(".preview-panel h2")?.focus();
      } else {
        view.querySelector(".upload-form button")?.focus();
      }
    }
  } catch (error) {
    if (error.status === 401) {
      location.replace("/login");
      return;
    }
    if (!form.isConnected) return;
    if (
      action === "/api/faculty/preview" ||
      (error.status === 409 && action.startsWith("/api/faculty/"))
    ) {
      await loadFaculty(view, new URLSearchParams(location.search));
    }
    setNotice(app, "error", error.message);
  }
}

async function showWorkspace(session, path, focusHeading = false) {
  const { title, breadcrumb = title, render } = routes[path];
  const params = new URLSearchParams(location.search);
  document.title = `${title} · AnimoRoll`;
  app.dataset.path = path;
  delete document.body.dataset.ready;
  const workspace = renderWorkspace(session, path, title, breadcrumb);
  const view = workspace.querySelector("[data-workspace-view]");
  app.replaceChildren(workspace);
  if (focusHeading) app.querySelector("h1").focus();
  await render(view, { params, user: session.user });
  if (view.isConnected) document.body.dataset.ready = "true";
}

async function start() {
  const session = await request("/api/session");
  if (location.pathname === "/login") {
    showLogin(app, session.csrf);
    document.body.dataset.ready = "true";
    return;
  }
  if (!session.user) {
    location.replace("/login");
    return;
  }
  const faculty = session.user.role === "FACULTY";
  const path = location.pathname;
  if (!Object.hasOwn(routes, path) || faculty !== (path === "/faculty")) {
    location.replace(faculty ? "/faculty" : "/");
    return;
  }
  app.addEventListener("submit", (event) => {
    const form = event.target;
    if (form.method !== "post") return;
    event.preventDefault();
    const view = app.querySelector("[data-workspace-view]");
    submitForm(form, () => workspaceAction(form, session, view));
  });
  window.addEventListener("popstate", () => {
    if (location.pathname !== app.dataset.path)
      showWorkspace(session, location.pathname, true);
  });
  await showWorkspace(session, path);
}

window.addEventListener("pageshow", (event) => {
  if (event.persisted) location.reload();
});

start().catch((error) => {
  const content = renderFeatureError(
    "We couldn’t open AnimoRoll.",
    error.message,
    () => location.reload(),
  );
  app.querySelector("main").replaceChildren(content);
  document.body.dataset.ready = "true";
});
