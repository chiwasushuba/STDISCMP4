import markup from "./login.html?raw";
import { setNotice } from "../components/feature-panel.js";
import { post } from "../lib/api.js";
import { createTemplates } from "../lib/dom.js";
import { submitForm } from "../lib/forms.js";

const clone = createTemplates(markup);

export function showLogin(app, csrf) {
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
      setNotice(app, "error", "");
      try {
        const user = await post(form, csrf);
        location.assign(user.role === "FACULTY" ? "/faculty" : "/");
      } catch (error) {
        setNotice(app, "error", error.message);
      }
    });
  });
}
