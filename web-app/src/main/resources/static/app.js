"use strict";
// Server validation remains authoritative; this only prevents accidental repeat clicks.
document.querySelectorAll("form[method='post']").forEach((form) => {
  form.addEventListener("submit", () => {
    const button = form.querySelector("button[type='submit']");
    if (button) {
      button.disabled = true;
      button.textContent = form.hasAttribute("data-enroll-form")
        ? "Enrolling…"
        : "Please wait…";
    }
  });
});
