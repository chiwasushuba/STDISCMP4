import markup from "./feature-panel.html?raw";
import { request } from "../lib/api.js";
import { createTemplates, setText } from "../lib/dom.js";

const clone = createTemplates(markup);

export function renderFeatureError(heading, message, retry) {
  const content = clone("feature-error");
  setText(content, "strong", heading);
  setText(content, "p", message);
  content.querySelector("button").addEventListener("click", retry);
  return content;
}

export function setNotice(root, kind, message) {
  for (const notice of root.querySelectorAll(
    ".notice.success, .notice.error",
  )) {
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

export async function loadFeature(
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
    const unavailable = renderFeatureError(errorHeading, error.message, () => {
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
