export async function submitForm(form, action) {
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
