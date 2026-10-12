export function navigateApp(page) {
  window.dispatchEvent(new CustomEvent('foodrun-open-page', { detail: page }));
}
