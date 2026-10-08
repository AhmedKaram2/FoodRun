// Accept our room links consistently in Hermes and browsers, including the custom scheme.
export function roomCodeFromLink(value: string): string | null {
  const match =
    /^(https|foodrun):\/\/([^/?#]+)(?:\/[^?#]*)?(?:\?([^#]*))?(?:#.*)?$/i.exec(
      value,
    );
  if (!match) return null;
  const protocol = match[1].toLowerCase(),
    host = match[2].toLowerCase();
  if (
    protocol === "https"
      ? !["intrvioo.com", "www.intrvioo.com"].includes(host)
      : host !== "join"
  )
    return null;
  const name = protocol === "https" ? "room" : "code";
  try {
    const pair = (match[3] || "")
      .split("&")
      .map((item) => item.split("="))
      .find((item) => decodeURIComponent(item[0]) === name);
    const code = pair ? decodeURIComponent(pair[1] || "") : "";
    return /^\d{6}$/.test(code) ? code : null;
  } catch {
    return null;
  }
}
