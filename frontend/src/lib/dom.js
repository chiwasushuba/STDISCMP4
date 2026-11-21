export function createTemplates(markup) {
  const templates = document.createElement("template");
  templates.innerHTML = markup;
  return (id) =>
    document.importNode(templates.content.getElementById(id).content, true);
}

export function setText(root, selector, value) {
  root.querySelector(selector).textContent = value;
}
