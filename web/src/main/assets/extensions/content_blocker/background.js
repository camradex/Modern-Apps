function isDomainBlocked(hostname) {
  let domain = hostname;
  while (domain.includes(".")) {
    if (BLOCKED_DOMAINS.has(domain)) return true;
    domain = domain.substring(domain.indexOf(".") + 1);
  }
  return false;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    // Let the Kotlin side handle top-level navigations (shows blocked screen UI)
    if (details.type === "main_frame") return {};
    try {
      const url = new URL(details.url);
      if (isDomainBlocked(url.hostname)) {
        return { cancel: true };
      }
    } catch (e) {
      // malformed URL, allow through
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);
