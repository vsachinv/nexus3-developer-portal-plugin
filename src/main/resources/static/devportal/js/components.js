/**
 * Developer Portal — Reusable UI components (pure functions → HTML strings)
 */

import { renderMarkdown } from './markdown.js';

const FORMAT_ICONS = {
  npm:    '📦',
  maven2: '☕',
  pypi:   '🐍',
  docker: '🐳',
  nuget:  '💜',
  helm:   '⛵',
  go:     '🔵',
};
const FORMAT_LABELS = {
  npm: 'npm', maven2: 'Maven', pypi: 'PyPI',
  docker: 'Docker', nuget: 'NuGet', helm: 'Helm', go: 'Go',
};

export function formatIcon(format) {
  return FORMAT_ICONS[format?.toLowerCase()] ?? '📁';
}
export function formatLabel(format) {
  return FORMAT_LABELS[format?.toLowerCase()] ?? (format ?? 'Unknown');
}

export function packageCard(pkg) {
  const fmt = (pkg.format ?? '').toLowerCase();
  return `
  <div class="package-card" data-format="${fmt}" data-name="${esc(pkg.name)}"
       data-group="${esc(pkg.group ?? '')}" data-repo="${esc(pkg.repository ?? '')}"
       role="button" tabindex="0" aria-label="${esc(pkg.name)}">
    <div class="package-card-header">
      <div class="package-icon ${fmt}">${formatIcon(fmt)}</div>
      <div>
        <div class="package-card-name">${esc(pkg.name)}</div>
        ${pkg.group ? `<div class="package-card-group">${esc(pkg.group)}</div>` : ''}
      </div>
    </div>
    ${pkg.description
      ? `<div class="package-card-description">${esc(pkg.description)}</div>`
      : ''}
    <div class="package-card-footer">
      <span class="badge badge-format ${fmt}">${formatLabel(fmt)}</span>
      ${pkg.latestVersion ? `<span class="badge badge-version">v${esc(pkg.latestVersion)}</span>` : ''}
      ${pkg.repository ? `<span class="badge badge-repo">${esc(pkg.repository)}</span>` : ''}
    </div>
  </div>`;
}

export function searchResultItem(pkg) {
  const fmt = (pkg.format ?? '').toLowerCase();
  return `
  <div class="search-result-item" data-format="${fmt}" data-name="${esc(pkg.name)}"
       data-group="${esc(pkg.group ?? '')}" data-repo="${esc(pkg.repository ?? '')}"
       role="button" tabindex="0">
    <div class="result-icon ${fmt}" style="background:var(--icon-bg, #f1f5f9)">${formatIcon(fmt)}</div>
    <div class="result-body">
      <div class="d-flex align-center gap-2">
        <span class="result-name">${esc(pkg.name)}</span>
        ${pkg.group ? `<span class="result-group">${esc(pkg.group)}</span>` : ''}
      </div>
      ${pkg.description
        ? `<div class="result-description">${esc(pkg.description)}</div>`
        : ''}
      <div class="result-meta">
        <span class="badge badge-format ${fmt}">${formatLabel(fmt)}</span>
        ${pkg.latestVersion ? `<span class="badge badge-version">v${esc(pkg.latestVersion)}</span>` : ''}
        ${pkg.repository    ? `<span class="badge badge-repo">${esc(pkg.repository)}</span>` : ''}
      </div>
    </div>
  </div>`;
}

export function packageDetailView(detail) {
  const fmt = (detail.format ?? '').toLowerCase();
  const versions = detail.versions ?? [];
  const snippets = detail.installSnippets ?? [];
  const shownVersion = detail.version ?? detail.latestVersion;
  const published = detail.lastModified
    ? new Date(detail.lastModified).toLocaleDateString(undefined,
        { year: 'numeric', month: 'short', day: 'numeric' })
    : null;

  // Deep-link into Nexus's own browse UI for this repository. Built from the
  // current origin so it stays correct regardless of host or context path.
  const nexusUrl = detail.repository
    ? `${location.origin}/#browse/browse:${encodeURIComponent(detail.repository)}`
    : null;

  return `
  <div class="package-detail">
    <div class="package-detail-header">
      <div class="package-icon ${fmt}">${formatIcon(fmt)}</div>
      <div class="package-detail-heading">
        <h1>${esc(detail.name)}</h1>
        <div class="package-detail-badges">
          <span class="badge badge-format ${fmt}">${formatLabel(fmt)}</span>
          ${shownVersion ? `<span class="badge badge-version">v${esc(shownVersion)}</span>` : ''}
          ${detail.repository ? `<span class="badge badge-repo">${esc(detail.repository)}</span>` : ''}
        </div>
      </div>
    </div>

    ${detail.description
      ? `<p class="package-detail-description">${esc(detail.description)}</p>` : ''}

    ${snippets.length ? installTabs(snippets) : ''}

    <div class="package-detail-body">
      <div class="package-main">
        ${contentTabs(detail)}
      </div>
      <aside class="package-sidebar">
        <div class="package-section">
          <h2>Versions</h2>
          ${versions.length
            ? `<ul class="version-list">${
                versions.map(v => {
                  const isCurrent = v === shownVersion;
                  const isLatest = v === detail.latestVersion;
                  return `<li class="${isCurrent ? 'current' : ''}">
                    <a href="${packageHref(detail, v)}">${esc(v)}</a>
                    ${isLatest ? '<span class="tag">latest</span>' : ''}
                  </li>`;
                }).join('')}</ul>`
            : `<p class="text-muted">No versions found.</p>`}
        </div>
        <div class="package-section">
          <h2>Details</h2>
          <dl class="meta-list">
            ${detail.author ? `<dt>Author</dt><dd>${esc(detail.author)}</dd>` : ''}
            ${detail.publishedBy ? `<dt>Published by</dt><dd>${esc(detail.publishedBy)}</dd>` : ''}
            ${detail.repository ? `<dt>Repository</dt><dd>${esc(detail.repository)}</dd>` : ''}
            ${detail.group ? `<dt>Namespace</dt><dd>${esc(detail.group)}</dd>` : ''}
            ${detail.latestVersion ? `<dt>Latest</dt><dd>${esc(detail.latestVersion)}</dd>` : ''}
            ${published ? `<dt>Published</dt><dd>${esc(published)}</dd>` : ''}
            ${(detail.links ?? []).map(l =>
              `<dt>${esc(l.label)}</dt><dd><a href="${esc(l.url)}" target="_blank" rel="noopener noreferrer">${esc(l.url)}</a></dd>`
            ).join('')}
          </dl>
          ${nexusUrl
            ? `<a class="nexus-link" href="${esc(nexusUrl)}" target="_blank" rel="noopener noreferrer">
                 View in Nexus Repository →
               </a>`
            : ''}
        </div>
      </aside>
    </div>
  </div>`;
}

/** Tabbed install snippets, one tab per tool. First tab active by default. */
function installTabs(snippets) {
  const tabs = snippets.map((s, i) =>
    `<button class="tab-btn${i === 0 ? ' active' : ''}" data-tab="${i}">${esc(s.label)}</button>`
  ).join('');
  const panels = snippets.map((s, i) => {
    const block = (s.code || '').includes('\n');
    return `<div class="install-box${block ? ' install-box-block' : ''} tab-panel${i === 0 ? '' : ' hidden'}" data-panel="${i}">
      <button class="copy-btn" data-copy="${esc(s.code)}" aria-label="Copy ${esc(s.label)} command">Copy</button>
      <code>${esc(s.code)}</code>
    </div>`;
  }).join('');
  return `
    <div class="package-section">
      <h2>Installation</h2>
      <div class="install-tabs tabs">
        <div class="tab-row">${tabs}</div>
        ${panels}
      </div>
    </div>`;
}

/** Verdaccio-style content tabs: Readme, Changelog (if any), Dependencies. */
function contentTabs(detail) {
  const deps = detail.dependencies ?? {};
  const depNames = Object.keys(deps);

  const panels = [];
  const labels = [];

  labels.push('Readme');
  panels.push(detail.readme
    ? `<div class="md-body">${renderMarkdown(detail.readme)}</div>`
    : `<p class="text-muted">No README available for this package.</p>`);

  if (detail.changelog) {
    labels.push('Changelog');
    panels.push(`<div class="md-body">${renderMarkdown(detail.changelog)}</div>`);
  }

  labels.push(`Dependencies${depNames.length ? ` (${depNames.length})` : ''}`);
  panels.push(depNames.length
    ? `<ul class="dep-list">${depNames.map(d => `<li>
         <span class="dep-name">${esc(d)}</span>
         <span class="dep-range">${esc(deps[d])}</span>
       </li>`).join('')}</ul>`
    : `<p class="text-muted">This package has no dependencies.</p>`);

  const tabRow = labels.map((label, i) =>
    `<button class="tab-btn${i === 0 ? ' active' : ''}" data-tab="${i}">${esc(label)}</button>`
  ).join('');
  const tabPanels = panels.map((html, i) =>
    `<div class="tab-panel${i === 0 ? '' : ' hidden'}" data-panel="${i}">${html}</div>`
  ).join('');

  return `<div class="content-tabs tabs">
    <div class="tab-row">${tabRow}</div>
    ${tabPanels}
  </div>`;
}

/** Builds a #/package hash link, optionally pinned to a version. */
function packageHref(detail, version) {
  const params = new URLSearchParams({ format: detail.format, name: detail.name });
  if (detail.group)      params.set('group', detail.group);
  if (detail.repository) params.set('repository', detail.repository);
  if (version)           params.set('version', version);
  return `#/package?${params}`;
}

export function repositoryCard(repo) {
  const fmt = (repo.format ?? '').toLowerCase();
  const online = repo.online !== false;
  return `
  <div class="repo-card">
    <div class="repo-card-header">
      <div class="repo-format-icon ${fmt}">${formatLabel(fmt)}</div>
      <div>
        <div class="repo-card-name">${esc(repo.name)}</div>
        <div class="repo-card-type">${esc(repo.type ?? '')}</div>
      </div>
    </div>
    <div class="repo-status">
      <span class="status-dot ${online ? 'online' : 'offline'}"></span>
      <span>${online ? 'Online' : 'Offline'}</span>
    </div>
  </div>`;
}

export function skeletonCards(count = 6) {
  return Array.from({ length: count }, () =>
    `<div class="skeleton skeleton-card"></div>`
  ).join('');
}

export function emptyState(title, message) {
  return `
  <div class="empty-state">
    <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
      <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
    </svg>
    <h3>${esc(title)}</h3>
    <p>${esc(message)}</p>
  </div>`;
}

export function spinner() {
  return `<div class="spinner" role="status" aria-label="Loading"></div>`;
}

/** Escape HTML to prevent XSS */
function esc(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
