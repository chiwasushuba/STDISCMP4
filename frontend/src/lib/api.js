const outageMessage =
  "This feature is temporarily unavailable. Please try again shortly.";

export function urlWithParams(path, entries) {
  const query = new URLSearchParams(entries).toString();
  return query ? `${path}?${query}` : path;
}

class PortalError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export async function request(path, options = {}) {
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

export function post(form, csrf) {
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
