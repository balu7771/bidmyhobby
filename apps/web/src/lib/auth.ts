export function authFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  return fetch(input, { ...init, credentials: "include" });
}

export function clearSession() {
  localStorage.removeItem("userEmail");
  localStorage.removeItem("userRole");
}
