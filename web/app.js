const MANIFEST_URL = "https://raw.githubusercontent.com/Anezium/RokidBrew-Registry/main/dist/apps.v1.json";
const NEW_DAYS = 30;

const state = {
  manifest: null,
  loading: true,
  error: null,
  query: "",
  category: "All",
  target: "All",
};

const appRoot = document.querySelector("#app");
const footer = document.querySelector("#siteFooter");
const phoneButton = document.querySelector("#phoneAppButton");
const lightbox = document.querySelector("#lightbox");
const lightboxImage = document.querySelector("#lightboxImage");

function escapeHtml(value = "") {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value = "") {
  return escapeHtml(value).replaceAll("`", "&#96;");
}

function safeUrl(value) {
  if (!value || typeof value !== "string") return "";
  try {
    const url = new URL(value, window.location.href);
    if (url.protocol === "http:" || url.protocol === "https:") return url.href;
  } catch {
    return "";
  }
  return "";
}

function formatDate(value) {
  if (!value) return "Unknown date";
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return escapeHtml(value);
  return new Intl.DateTimeFormat(undefined, { year: "numeric", month: "short", day: "numeric" }).format(date);
}

function formatBytes(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "size unknown";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  const digits = unit === 0 || size >= 10 ? 0 : 1;
  return `${size.toFixed(digits)} ${units[unit]}`;
}

function initials(name = "") {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  const text = parts.length > 1 ? `${parts[0][0]}${parts[1][0]}` : String(name).slice(0, 2);
  return escapeHtml(text.toUpperCase() || "RB");
}

function targetsFor(app) {
  return [...new Set((app.artifacts || []).map((artifact) => artifact?.target).filter(Boolean))];
}

function hasTarget(app, target) {
  if (target === "All") return true;
  return targetsFor(app).includes(target.toLowerCase());
}

function isNew(app) {
  if (!app?.publishedAt) return false;
  const published = new Date(app.publishedAt);
  if (Number.isNaN(published.valueOf())) return false;
  const ageMs = Date.now() - published.getTime();
  return ageMs >= 0 && ageMs < NEW_DAYS * 24 * 60 * 60 * 1000;
}

function targetBadges(app, includeNew = false) {
  const targetHtml = targetsFor(app)
    .map((target) => `<span class="badge ${target === "phone" ? "phone" : "glasses"}">${escapeHtml(target.toUpperCase())}</span>`)
    .join("");
  const newHtml = includeNew && isNew(app) ? '<span class="badge new">NEW</span>' : "";
  return `<span class="badges">${targetHtml}${newHtml}</span>`;
}

function iconHtml(app, className = "app-icon") {
  const url = safeUrl(app?.iconUrl);
  const fallback = `<span class="icon-fallback" aria-hidden="true">${initials(app?.name)}</span>`;
  if (!url) return fallback;
  return `<img class="${className}" loading="lazy" src="${escapeAttr(url)}" alt="" data-fallback="${escapeAttr(initials(app?.name))}">`;
}

function setImageFallbacks(root = document) {
  root.querySelectorAll("img[data-fallback]").forEach((img) => {
    img.addEventListener("error", () => {
      const span = document.createElement("span");
      span.className = "icon-fallback";
      span.setAttribute("aria-hidden", "true");
      span.textContent = img.dataset.fallback || "RB";
      img.replaceWith(span);
    }, { once: true });
  });
}

function getCategories() {
  const apps = state.manifest?.apps || [];
  return ["All", ...[...new Set(apps.map((app) => app.category).filter(Boolean))].sort((a, b) => a.localeCompare(b))];
}

function filteredApps() {
  const apps = state.manifest?.apps || [];
  const query = state.query.trim().toLowerCase();
  return apps.filter((app) => {
    const categoryOk = state.category === "All" || app.category === state.category;
    const targetOk = hasTarget(app, state.target);
    const searchText = [app.name, app.summary, app.author].filter(Boolean).join(" ").toLowerCase();
    const queryOk = !query || searchText.includes(query);
    return categoryOk && targetOk && queryOk;
  });
}

function renderLoading() {
  appRoot.innerHTML = `
    <section class="hero">
      <div>
        <h1>Rokid<span class="green-text">Brew</span></h1>
        <p>Loading the community registry...</p>
      </div>
      <span class="status-pill">manifest: connecting</span>
    </section>
    <div class="skeleton-grid" aria-label="Loading apps">
      ${Array.from({ length: 8 }, () => '<div class="skeleton-card"></div>').join("")}
    </div>
  `;
}

function renderError() {
  appRoot.innerHTML = `
    <section class="error-state">
      <div>
        <h2>Could not load the store</h2>
        <p>${escapeHtml(state.error?.message || "The app registry did not respond.")}</p>
        <button class="button" type="button" id="retryButton">Retry</button>
      </div>
    </section>
  `;
  document.querySelector("#retryButton")?.addEventListener("click", loadManifest);
}

function renderHome() {
  const apps = filteredApps();
  const allApps = state.manifest?.apps || [];
  const categories = getCategories();
  const generated = state.manifest?.generatedAt ? formatDate(state.manifest.generatedAt) : "unknown";
  const categoryChips = categories.map((category) => `
    <button class="chip ${state.category === category ? "is-active" : ""}" type="button" data-category="${escapeAttr(category)}">
      ${escapeHtml(category)}
    </button>
  `).join("");
  const targetChips = ["All", "Glasses", "Phone"].map((target) => `
    <button class="chip ${state.target === target ? "is-active" : ""}" type="button" data-target="${escapeAttr(target)}">
      ${escapeHtml(target)}
    </button>
  `).join("");

  appRoot.innerHTML = `
    <section class="hero">
      <div>
        <h1>Rokid<span class="green-text">Brew</span></h1>
        <p>Unofficial community app store for Rokid Glasses apps, powered by the live registry.</p>
      </div>
      <span class="status-pill">${allApps.length} apps / generated ${generated}</span>
    </section>

    <section class="toolbar" aria-label="Store filters">
      <label class="search-wrap">
        <span class="sr-only">Search apps</span>
        <input class="search-input" id="searchInput" type="search" value="${escapeAttr(state.query)}" placeholder="Search name, summary, or author" autocomplete="off">
      </label>
      <div class="filter-block">
        <span class="filter-label">Category</span>
        <div class="chips" id="categoryChips">${categoryChips}</div>
      </div>
      <div class="filter-block">
        <span class="filter-label">Target</span>
        <div class="chips" id="targetChips">${targetChips}</div>
      </div>
    </section>

    <div class="grid-meta">
      <span>${apps.length} ${apps.length === 1 ? "app" : "apps"} shown</span>
      <span>${escapeHtml(state.target)} target</span>
    </div>

    ${apps.length ? `<section class="app-grid">${apps.map(renderCard).join("")}</section>` : renderEmpty()}
  `;

  document.querySelector("#searchInput")?.addEventListener("input", (event) => {
    state.query = event.target.value;
    const caret = event.target.selectionStart;
    render({ preserveFocus: true });
    const input = document.querySelector("#searchInput");
    if (input) {
      input.focus({ preventScroll: true });
      if (caret !== null) input.setSelectionRange(caret, caret);
    }
  });
  document.querySelector("#categoryChips")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-category]");
    if (!button) return;
    state.category = button.dataset.category;
    render();
  });
  document.querySelector("#targetChips")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-target]");
    if (!button) return;
    state.target = button.dataset.target;
    render();
  });
  setImageFallbacks(appRoot);
}

function renderCard(app) {
  return `
    <a class="app-card" href="#/app/${encodeURIComponent(app.id)}">
      <div class="card-top">
        ${iconHtml(app)}
        <div class="card-title">
          <h2>${escapeHtml(app.name || "Untitled app")}</h2>
          <span class="category">${escapeHtml(app.category || "Uncategorized")}</span>
        </div>
      </div>
      <p class="summary">${escapeHtml(app.summary || app.description || "No summary available.")}</p>
      <div class="card-footer">
        <span class="version">v${escapeHtml(app.version || "unknown")}</span>
        ${targetBadges(app)}
      </div>
    </a>
  `;
}

function renderEmpty() {
  return `
    <section class="empty-state">
      <div>
        <h2>No matching apps</h2>
        <p>Try a different search, category, or target filter.</p>
      </div>
    </section>
  `;
}

function renderDetail(id) {
  const app = (state.manifest?.apps || []).find((item) => item.id === id);
  if (!app) {
    appRoot.innerHTML = `
      <section class="empty-state">
        <div>
          <h2>App not found</h2>
          <p>The registry does not include an app with id <code>${escapeHtml(id)}</code>.</p>
          <a class="button" href="#/">Back to store</a>
        </div>
      </section>
    `;
    return;
  }

  const screenshots = (app.screenshotUrls || []).map(safeUrl).filter(Boolean);
  const description = app.listing?.descriptionMarkdown || app.listing?.about || app.description || app.summary || "No description available.";
  const latestRelease = Array.isArray(app.releases) && app.releases.length
    ? [...app.releases].sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0))[0]
    : null;

  appRoot.innerHTML = `
    <article class="detail">
      <a class="back-link" href="#/">← Store</a>
      <section class="detail-hero">
        ${iconHtml(app, "detail-icon")}
        <div>
          <h1>${escapeHtml(app.name || "Untitled app")}</h1>
          <div class="detail-meta">
            <span>${escapeHtml(app.author || "Unknown author")}</span>
            <span>${escapeHtml(app.category || "Uncategorized")}</span>
            <span>v${escapeHtml(app.version || "unknown")}</span>
            <span class="detail-date">${formatDate(app.publishedAt)}</span>
          </div>
          <div style="margin-top: 12px">${targetBadges(app, true)}</div>
        </div>
      </section>

      ${screenshots.length ? `
        <section class="screenshots" aria-label="Screenshots">
          ${screenshots.map((src, index) => `
            <button class="screenshot-button" type="button" data-screenshot="${escapeAttr(src)}">
              <img loading="lazy" src="${escapeAttr(src)}" alt="${escapeAttr(`${app.name || "App"} screenshot ${index + 1}`)}">
            </button>
          `).join("")}
        </section>
      ` : ""}

      <div class="detail-layout">
        <section class="panel">
          <div class="panel-inner">
            <h2>About</h2>
            <div class="markdown">${renderMarkdown(description)}</div>
          </div>
        </section>

        <aside class="panel">
          <div class="panel-inner">
            <h2>Downloads</h2>
            <div class="artifact-list">${renderArtifacts(app)}</div>
            ${safeUrl(app.sourceUrl) ? `<a class="source-link" href="${escapeAttr(safeUrl(app.sourceUrl))}" target="_blank" rel="noopener">Source repository</a>` : ""}
          </div>
        </aside>
      </div>

      ${latestRelease ? renderRelease(latestRelease) : ""}
    </article>
  `;

  appRoot.querySelectorAll("[data-screenshot]").forEach((button) => {
    button.addEventListener("click", () => openLightbox(button.dataset.screenshot, button.querySelector("img")?.alt || ""));
  });
  appRoot.querySelectorAll("[data-copy]").forEach((button) => {
    button.addEventListener("click", () => copyHash(button));
  });
  setImageFallbacks(appRoot);
}

function renderArtifacts(app) {
  const artifacts = Array.isArray(app.artifacts) ? app.artifacts : [];
  if (!artifacts.length) return '<p class="summary">No downloadable APK artifacts are listed for this app.</p>';
  return artifacts.map((artifact) => {
    const target = artifact.target || "app";
    const version = artifact.versionName || app.version || "unknown";
    const url = safeUrl(artifact.url);
    const hash = artifact.sha256 || "";
    return `
      <div class="artifact">
        ${url ? `
          <a class="download-button" href="${escapeAttr(url)}">
            Download for ${escapeHtml(capitalize(target))} — v${escapeHtml(version)} · ${escapeHtml(formatBytes(artifact.sizeBytes))}
          </a>
        ` : '<span class="download-button" aria-disabled="true">Download unavailable</span>'}
        <span class="size">${escapeHtml(artifact.packageName || "package unknown")}</span>
        ${hash ? `
          <span class="hash-row">
            <span class="hash-value">sha256 ${escapeHtml(hash.slice(0, 12))}…</span>
            <button class="copy-button" type="button" data-copy="${escapeAttr(hash)}">Copy</button>
          </span>
        ` : ""}
      </div>
    `;
  }).join("");
}

function renderRelease(release) {
  const changes = Array.isArray(release.changes) ? release.changes.filter(Boolean) : [];
  const notes = release.notes ? renderMarkdown(release.notes) : "";
  const changeHtml = changes.length
    ? `${notes ? "<h3>Changes</h3>" : ""}<ul>${changes.map((change) => `<li>${escapeHtml(change)}</li>`).join("")}</ul>`
    : "";
  if (!notes && !changeHtml) return "";
  return `
    <section class="panel">
      <div class="panel-inner release-notes">
        <h2>What's new ${release.version ? `<span class="version">v${escapeHtml(release.version)}</span>` : ""}</h2>
        ${notes}${changeHtml}
      </div>
    </section>
  `;
}

function capitalize(value = "") {
  return String(value).slice(0, 1).toUpperCase() + String(value).slice(1);
}

function renderFooter() {
  const count = state.manifest?.apps?.length || 0;
  const generated = state.manifest?.generatedAt ? formatDate(state.manifest.generatedAt) : "unknown";
  footer.innerHTML = `
    <div class="footer-links">
      <a href="https://github.com/Anezium/RokidBrew" target="_blank" rel="noopener">RokidBrew</a>
      <a href="https://github.com/Anezium/RokidBrew-Registry" target="_blank" rel="noopener">Registry</a>
    </div>
    <div>${count} apps / manifest ${generated}</div>
    <div>Unofficial community project — not affiliated with Rokid.</div>
  `;
}

function updatePhoneButton() {
  const url = safeUrl(state.manifest?.brewApkUrl);
  if (!url) {
    phoneButton.href = "#";
    phoneButton.textContent = "Get the phone app";
    phoneButton.classList.add("is-loading");
    phoneButton.setAttribute("aria-disabled", "true");
    return;
  }
  phoneButton.href = url;
  phoneButton.textContent = `Get the phone app v${state.manifest?.brewVersion || ""}`.trim();
  phoneButton.classList.remove("is-loading");
  phoneButton.removeAttribute("aria-disabled");
}

function route() {
  const hash = window.location.hash || "#/";
  const match = hash.match(/^#\/app\/(.+)$/);
  if (match) {
    try {
      return { name: "detail", id: decodeURIComponent(match[1]) };
    } catch {
      return { name: "detail", id: match[1] };
    }
  }
  return { name: "home" };
}

function render(options = {}) {
  updatePhoneButton();
  renderFooter();
  if (state.loading) {
    renderLoading();
  } else if (state.error) {
    renderError();
  } else {
    const current = route();
    if (current.name === "detail") renderDetail(current.id);
    else renderHome();
  }
  if (!options.preserveFocus) appRoot.focus({ preventScroll: true });
}

async function loadManifest() {
  state.loading = true;
  state.error = null;
  render();
  try {
    const response = await fetch(MANIFEST_URL, { cache: "no-store" });
    if (!response.ok) throw new Error(`Registry returned HTTP ${response.status}.`);
    const manifest = await response.json();
    if (!manifest || !Array.isArray(manifest.apps)) throw new Error("Registry response did not include an apps array.");
    state.manifest = manifest;
  } catch (error) {
    state.error = error;
  } finally {
    state.loading = false;
    render();
  }
}

function renderMarkdown(markdown = "") {
  const lines = String(markdown).replace(/\r\n?/g, "\n").split("\n");
  const html = [];
  let paragraph = [];
  let list = null;
  let inFence = false;
  let fenceLines = [];

  const flushParagraph = () => {
    if (!paragraph.length) return;
    html.push(`<p>${renderInline(paragraph.join(" "))}</p>`);
    paragraph = [];
  };
  const closeList = () => {
    if (!list) return;
    html.push(`</${list}>`);
    list = null;
  };

  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      if (inFence) {
        html.push(`<pre><code>${escapeHtml(fenceLines.join("\n"))}</code></pre>`);
        fenceLines = [];
        inFence = false;
      } else {
        flushParagraph();
        closeList();
        inFence = true;
      }
      continue;
    }

    if (inFence) {
      fenceLines.push(line);
      continue;
    }

    if (!line.trim()) {
      flushParagraph();
      closeList();
      continue;
    }

    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    if (heading) {
      flushParagraph();
      closeList();
      const level = heading[1].length;
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`);
      continue;
    }

    const bullet = /^\s*[-*]\s+(.+)$/.exec(line);
    const ordered = /^\s*\d+[.)]\s+(.+)$/.exec(line);
    if (bullet || ordered) {
      flushParagraph();
      const type = bullet ? "ul" : "ol";
      if (list && list !== type) closeList();
      if (!list) {
        list = type;
        html.push(`<${type}>`);
      }
      html.push(`<li>${renderInline((bullet || ordered)[1])}</li>`);
      continue;
    }

    paragraph.push(line.trim());
  }

  if (inFence) html.push(`<pre><code>${escapeHtml(fenceLines.join("\n"))}</code></pre>`);
  flushParagraph();
  closeList();
  return html.join("");
}

function renderInline(text) {
  const codeTokens = [];
  let escaped = escapeHtml(text).replace(/`([^`]+)`/g, (_, code) => {
    const token = `\u0000CODE${codeTokens.length}\u0000`;
    codeTokens.push(`<code>${code}</code>`);
    return token;
  });

  escaped = escaped.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, href) => {
    const decodedHref = href
      .replaceAll("&amp;", "&")
      .replaceAll("&quot;", '"')
      .replaceAll("&#39;", "'");
    const url = safeUrl(decodedHref);
    if (!url) return label;
    return `<a href="${escapeAttr(url)}" target="_blank" rel="noopener">${label}</a>`;
  });
  escaped = escaped.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  escaped = escaped.replace(/\*([^*]+)\*/g, "<em>$1</em>");
  escaped = escaped.replace(/__([^_]+)__/g, "<strong>$1</strong>");
  escaped = escaped.replace(/_([^_]+)_/g, "<em>$1</em>");

  codeTokens.forEach((token, index) => {
    escaped = escaped.replace(`\u0000CODE${index}\u0000`, token);
  });
  return escaped;
}

async function copyHash(button) {
  const value = button.dataset.copy || "";
  try {
    await navigator.clipboard.writeText(value);
    const old = button.textContent;
    button.textContent = "Copied";
    window.setTimeout(() => {
      button.textContent = old;
    }, 1200);
  } catch {
    button.textContent = "Select";
  }
}

function openLightbox(src, alt) {
  lightboxImage.src = src;
  lightboxImage.alt = alt;
  lightbox.hidden = false;
  document.body.style.overflow = "hidden";
}

function closeLightbox() {
  lightbox.hidden = true;
  lightboxImage.removeAttribute("src");
  document.body.style.overflow = "";
}

lightbox.addEventListener("click", (event) => {
  if (event.target === lightbox || event.target.closest(".lightbox-close")) closeLightbox();
});

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !lightbox.hidden) closeLightbox();
});

window.addEventListener("hashchange", render);

loadManifest();
