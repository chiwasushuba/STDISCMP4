import "./styles.css";

const app = document.querySelector("#app");
const routeTitles = {
  "/": ["dashboard", "Semester overview", "Overview"],
  "/courses": ["courses", "Course catalog"],
  "/enrollment": ["enrollment", "My enrollment"],
  "/grades": ["grades", "Grade history"],
  "/faculty": ["faculty", "Classes & grades"],
};
const outageMessage =
  "This feature is temporarily unavailable. Please try again shortly.";

function clone(id) {
  return document.importNode(document.getElementById(id).content, true);
}

function setText(root, selector, value) {
  root.querySelector(selector).textContent = value;
}

function urlWithParams(path, entries) {
  const query = new URLSearchParams(entries).toString();
  return query ? `${path}?${query}` : path;
}

class PortalError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(path, options);
  } catch {
    throw new PortalError(outageMessage);
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new PortalError(error?.message || outageMessage, response.status);
  }
  if (response.status !== 204) {
    return response.json().catch(() => {
      throw new PortalError(outageMessage);
    });
  }
}

function post(form, csrf) {
  const fields = new FormData(form);
  return request(form.getAttribute("action"), {
    method: "POST",
    headers: { [csrf.headerName]: csrf.token },
    body:
      form.enctype === "multipart/form-data"
        ? fields
        : new URLSearchParams(fields),
  });
}

function setNotice(kind, message) {
  for (const notice of app.querySelectorAll(".notice.success, .notice.error")) {
    notice.hidden = !message || !notice.classList.contains(kind);
    if (!notice.hidden) {
      notice.textContent = message;
      if (kind === "error") {
        notice.tabIndex = -1;
        notice.focus();
      }
    }
  }
}

async function submitForm(form, action) {
  const button = form.querySelector('button[type="submit"]');
  if (button.disabled) return;
  const label = [...button.childNodes];
  button.disabled = true;
  button.textContent = form.hasAttribute("data-enroll-form")
    ? "Enrolling…"
    : "Please wait…";
  try {
    await action();
  } finally {
    button.disabled = false;
    button.replaceChildren(...label);
  }
}

function showLogin(csrf) {
  document.body.classList.add("login-body");
  document.title = "Sign in · AnimoRoll";
  app.replaceChildren(clone("login"));
  const params = new URLSearchParams(location.search);
  app.querySelector(".notice.success").hidden = !params.has("signedout");
  app.querySelector(".notice.warning").hidden =
    !params.has("revocationPending");
  const form = app.querySelector("form");
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitForm(form, async () => {
      setNotice("error", "");
      try {
        const user = await post(form, csrf);
        location.assign(user.role === "FACULTY" ? "/faculty" : "/");
      } catch (error) {
        setNotice("error", error.message);
      }
    });
  });
}

async function loadFeature(
  root,
  path,
  loadingText,
  errorHeading,
  render,
  retry = false,
) {
  // The live region stays outside the busy panel so retries can announce progress.
  const announcement = root.nextElementSibling;
  const pending = document.createElement("p");
  pending.className = "feature-loading";
  pending.textContent = loadingText;
  announcement.textContent = retry ? loadingText : "";
  root.setAttribute("aria-busy", "true");
  root.replaceChildren(pending);
  try {
    root.replaceChildren(render(await request(path)));
    if (retry)
      announcement.textContent = `${root.getAttribute("aria-label")} updated.`;
  } catch (error) {
    if (!root.isConnected) return;
    if (error.status === 401) {
      location.replace("/login");
      return;
    }
    const unavailable = clone("feature-error");
    setText(unavailable, "strong", errorHeading);
    setText(unavailable, "p", error.message);
    unavailable.querySelector("button").addEventListener("click", () => {
      root.tabIndex = -1;
      root.focus();
      loadFeature(root, path, loadingText, errorHeading, render, true);
    });
    root.replaceChildren(unavailable);
    announcement.textContent = `${errorHeading} ${error.message}`;
  } finally {
    root.removeAttribute("aria-busy");
  }
}

function courseGrid(courses) {
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

function setEnrollmentCounts(root, enrolled) {
  setText(root, "[data-enrolled-count]", enrolled.length);
  setText(
    root,
    "[data-unit-count]",
    enrolled.reduce((sum, course) => sum + course.units, 0),
  );
}

function loadDashboard(view, user) {
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

function loadCourses(view, params) {
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

function loadEnrollment(view) {
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

function loadGrades(view, params) {
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

function loadFaculty(view, params) {
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

const loadView = {
  dashboard: (view, _params, user) => loadDashboard(view, user),
  courses: loadCourses,
  enrollment: loadEnrollment,
  grades: loadGrades,
  faculty: loadFaculty,
};

async function workspaceAction(form, session, view) {
  const action = form.getAttribute("action");
  setNotice("error", "");
  try {
    const result = await post(form, session.csrf);
    if (!form.isConnected) return;
    if (action === "/api/signout") {
      const suffix = result.revocationPending ? "&revocationPending" : "";
      location.assign(`/login?signedout${suffix}`);
    } else if (action === "/api/enroll") {
      history.pushState(null, "", "/enrollment");
      await showWorkspace(session, "/enrollment", true);
      setNotice("success", result.message);
    } else {
      await loadFaculty(view, new URLSearchParams(location.search));
      if (result?.message) {
        setNotice("success", result.message);
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
    setNotice("error", error.message);
  }
}

async function showWorkspace(session, path, focusHeading = false) {
  const params = new URLSearchParams(location.search);
  const [page, title, breadcrumb = title] = routeTitles[path];
  const { user, currentTerm } = session;
  const faculty = user.role === "FACULTY";
  document.title = `${title} · AnimoRoll`;
  app.dataset.path = path;
  delete document.body.dataset.ready;
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
  const view = workspace.querySelector("[data-workspace-view]");
  view.replaceChildren(clone(page));
  app.replaceChildren(workspace);
  if (focusHeading) app.querySelector("h1").focus();
  await loadView[page](view, params, user);
  if (view.isConnected) document.body.dataset.ready = "true";
}

async function start() {
  const session = await request("/api/session");
  if (location.pathname === "/login") {
    showLogin(session.csrf);
    document.body.dataset.ready = "true";
    return;
  }
  if (!session.user) {
    location.replace("/login");
    return;
  }
  const faculty = session.user.role === "FACULTY";
  const path = location.pathname;
  if (!Object.hasOwn(routeTitles, path) || faculty !== (path === "/faculty")) {
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
  const content = clone("feature-error");
  setText(content, "strong", "We couldn’t open AnimoRoll.");
  setText(content, "p", error.message);
  content
    .querySelector("button")
    .addEventListener("click", () => location.reload());
  app.querySelector("main").replaceChildren(content);
  document.body.dataset.ready = "true";
});
