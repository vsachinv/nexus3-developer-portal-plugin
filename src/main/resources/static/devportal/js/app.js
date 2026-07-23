/**
 * Developer Portal — Main application
 * Hash-based SPA router: #/ → home, #/search → search, #/repos → repos
 */

import { api } from './api.js';
import {
  packageCard, searchResultItem, repositoryCard, packageDetailView,
  skeletonCards, emptyState, spinner, formatLabel,
} from './components.js';

// ─── Router ────────────────────────────────────────────────────────────────

const ROUTES = {
  '':         renderHome,
  'search':   renderSearch,
  'repos':    renderRepositories,
  'package':  renderPackage,
};

function route() {
  // Strip any query string before splitting into path segments — the page
  // segment must be e.g. "search", not "search?q=foo".
  const hash  = location.hash.replace(/^#\/?/, '').split('?')[0];
  const parts = hash.split('/');
  const page  = parts[0] || '';
  const handler = ROUTES[page];
  if (handler) {
    syncNav(page);
    handler(parts.slice(1));
  } else {
    renderHome([]);
  }
}

function syncNav(page) {
  document.querySelectorAll('[data-nav]').forEach(el => {
    el.classList.toggle('active', el.dataset.nav === page);
  });
}

function navigate(hash) {
  location.hash = '#/' + hash;
}

// ─── Home page ──────────────────────────────────────────────────────────────

async function renderHome() {
  setPageTitle('Developer Portal');
  getContent().innerHTML = `
    ${heroSection()}
    <div class="page-content">
      <div class="container">
        <section class="section" id="section-recent">
          <div class="section-header">
            <h2>Recently Published</h2>
            <a href="#/search">View all</a>
          </div>
          <div class="package-grid" id="recent-grid">${skeletonCards(6)}</div>
        </section>
        <section class="section" id="section-repos">
          <div class="section-header">
            <h2>Repositories</h2>
            <a href="#/repos">View all</a>
          </div>
          <div class="repo-grid" id="repos-grid">${skeletonCards(4)}</div>
        </section>
      </div>
    </div>`;

  wireHeroSearch();

  const [recent, repos] = await Promise.allSettled([
    api.recent(6),
    api.repositories(),
  ]);

  renderRecentGrid(
    recent.status === 'fulfilled' ? recent.value : [],
    document.getElementById('recent-grid'),
  );

  renderRepoGrid(
    repos.status === 'fulfilled' ? (repos.value.slice ? repos.value.slice(0, 8) : repos.value) : [],
    document.getElementById('repos-grid'),
  );
}

function heroSection() {
  return `
  <div class="hero">
    <div class="container hero-content">
      <h1>The <span>Developer Portal</span><br>for your Nexus packages</h1>
      <p>Search, discover, and install packages from all your Nexus repositories in one place.</p>
      <form class="hero-search" id="hero-form" onsubmit="return false">
        <input type="search" id="hero-input" placeholder="Search packages…" autocomplete="off" autofocus>
        <button type="submit" id="hero-btn">Search</button>
      </form>
      <div class="hero-stats" id="hero-stats">
        <div><strong id="stat-repos">—</strong>Repositories</div>
        <div><strong id="stat-formats">—</strong>Formats</div>
      </div>
    </div>
  </div>`;
}

function wireHeroSearch() {
  const form  = document.getElementById('hero-form');
  const input = document.getElementById('hero-input');
  if (!form || !input) return;

  form.addEventListener('submit', () => {
    const q = input.value.trim();
    if (q) navigate(`search?q=${encodeURIComponent(q)}`);
  });

  // Fetch stats
  api.repositories().then(repos => {
    const arr = Array.isArray(repos) ? repos : [];
    const formats = new Set(arr.map(r => r.format));
    const elR = document.getElementById('stat-repos');
    const elF = document.getElementById('stat-formats');
    if (elR) elR.textContent = arr.length;
    if (elF) elF.textContent = formats.size;
  }).catch(() => {});
}

function renderRecentGrid(packages, container) {
  if (!container) return;
  if (!packages || packages.length === 0) {
    container.innerHTML = emptyState('No packages yet', 'Push a package to get started.');
    return;
  }
  container.innerHTML = packages.map(packageCard).join('');
  attachCardHandlers(container);
}

function renderRepoGrid(repos, container) {
  if (!container) return;
  if (!repos || repos.length === 0) {
    container.innerHTML = emptyState('No repositories found',
      'Configure a supported format repository in Nexus admin.');
    return;
  }
  container.innerHTML = repos.map(repositoryCard).join('');
}

// ─── Search page ────────────────────────────────────────────────────────────

async function renderSearch(parts) {
  const params = new URLSearchParams(location.hash.split('?')[1] ?? '');
  const q      = params.get('q') ?? '';
  const format = params.get('format') ?? '';
  let   page   = parseInt(params.get('page') ?? '0', 10);
  if (isNaN(page) || page < 0) page = 0;

  setPageTitle(q ? `Search: ${q}` : 'Search');
  getContent().innerHTML = `
    <div class="page-content">
      <div class="container">
        <div class="mt-6">
          <form id="search-form" onsubmit="return false" style="display:flex;gap:8px;margin-bottom:24px">
            <input id="search-input" type="search" value="${escAttr(q)}"
              placeholder="Search packages…" autocomplete="off"
              style="flex:1;height:44px;padding:0 16px;border:1px solid var(--color-border);
                     border-radius:var(--radius-md);font-size:.95rem;outline:none">
            <button type="submit"
              style="height:44px;padding:0 20px;background:var(--color-primary);color:white;
                     border:none;border-radius:var(--radius-md);font-weight:600;font-size:.9rem">
              Search
            </button>
          </form>
          <div class="filter-bar" id="format-filters">
            ${renderFormatFilters(format)}
          </div>
          <div id="search-results">${spinner()}</div>
        </div>
      </div>
    </div>`;

  wireSearchForm(q, format);

  if (!q && !format) {
    document.getElementById('search-results').innerHTML =
      emptyState('Start searching', 'Type a package name or keyword above.');
    return;
  }

  try {
    const result = await api.search(q, { format: format || undefined, page, pageSize: 20 });
    renderSearchResults(result, q);
  } catch (err) {
    document.getElementById('search-results').innerHTML =
      emptyState('Search failed', err.message);
  }
}

function renderFormatFilters(active) {
  const formats = ['', 'npm', 'maven2', 'pypi', 'docker', 'nuget'];
  const labels  = ['All', 'npm', 'Maven', 'PyPI', 'Docker', 'NuGet'];
  return formats.map((f, i) =>
    `<button class="filter-btn${f === active ? ' active' : ''}"
      data-format="${f}">${labels[i]}</button>`
  ).join('');
}

function wireSearchForm(initialQ, initialFormat) {
  const form   = document.getElementById('search-form');
  const input  = document.getElementById('search-input');
  const filters = document.getElementById('format-filters');

  form?.addEventListener('submit', () => {
    const q = input?.value.trim() ?? '';
    const fmt = document.querySelector('.filter-btn.active')?.dataset.format ?? '';
    navigate(`search?q=${encodeURIComponent(q)}${fmt ? '&format=' + fmt : ''}`);
  });

  filters?.addEventListener('click', e => {
    const btn = e.target.closest('.filter-btn');
    if (!btn) return;
    const q   = input?.value.trim() ?? '';
    const fmt = btn.dataset.format;
    navigate(`search?q=${encodeURIComponent(q)}${fmt ? '&format=' + fmt : ''}`);
  });
}

function renderSearchResults(result, query) {
  const container = document.getElementById('search-results');
  if (!container) return;

  const items = result?.items ?? [];
  const total = result?.totalCount ?? 0;

  if (items.length === 0) {
    container.innerHTML = emptyState(
      `No results for "${query}"`,
      'Try a different keyword, or check your repository configuration.',
    );
    return;
  }

  container.innerHTML = `
    <div class="search-results-header">
      <h2>${escHTML(query ? `Results for "${query}"` : 'All packages')}</h2>
      <span class="result-count">${total} package${total !== 1 ? 's' : ''}</span>
    </div>
    <div id="result-list">${items.map(searchResultItem).join('')}</div>
    ${result?.hasMore ? paginationHint() : ''}`;

  attachCardHandlers(container);
}

function paginationHint() {
  return `<div style="text-align:center;padding:24px;color:var(--color-text-muted);font-size:.875rem">
    More results available — refine your search to narrow down.
  </div>`;
}

// ─── Repositories page ──────────────────────────────────────────────────────

async function renderRepositories() {
  setPageTitle('Repositories');
  getContent().innerHTML = `
    <div class="page-content">
      <div class="container">
        <section class="section">
          <div class="section-header">
            <h2>All Repositories</h2>
          </div>
          <div class="filter-bar" id="repo-format-filters">
            ${renderFormatFilters('')}
          </div>
          <div class="repo-grid" id="repos-full-grid">${skeletonCards(8)}</div>
        </section>
      </div>
    </div>`;

  document.getElementById('repo-format-filters')?.addEventListener('click', async e => {
    const btn = e.target.closest('.filter-btn');
    if (!btn) return;
    document.querySelectorAll('#repo-format-filters .filter-btn').forEach(b =>
      b.classList.toggle('active', b === btn));
    await loadRepos(btn.dataset.format || undefined);
  });

  await loadRepos();
}

async function loadRepos(format) {
  const grid = document.getElementById('repos-full-grid');
  if (!grid) return;
  grid.innerHTML = skeletonCards(8);
  try {
    const repos = await api.repositories(format);
    renderRepoGrid(Array.isArray(repos) ? repos : [], grid);
  } catch (err) {
    grid.innerHTML = emptyState('Failed to load repositories', err.message);
  }
}

// ─── Package detail page ──────────────────────────────────────────────────────

async function renderPackage() {
  const params     = new URLSearchParams(location.hash.split('?')[1] ?? '');
  const format     = params.get('format') ?? '';
  const name       = params.get('name') ?? '';
  const group      = params.get('group') ?? '';
  const repository = params.get('repository') ?? '';
  const version    = params.get('version') ?? '';

  setPageTitle(name || 'Package');
  getContent().innerHTML = `
    <div class="page-content"><div class="container">
      <div class="mt-6"><a href="#/search" class="back-link">← Back to search</a></div>
      <div id="package-detail">${spinner()}</div>
    </div></div>`;

  const target = document.getElementById('package-detail');
  if (!format || !name) {
    target.innerHTML = emptyState('Package not specified', 'No package was selected.');
    return;
  }

  try {
    const detail = await api.packageDetail({ format, name, group, repository, version });
    target.innerHTML = packageDetailView(detail);
    wireCopyButtons(target);
    wireTabs(target);
  } catch (err) {
    const notFound = /404/.test(err.message);
    target.innerHTML = notFound
      ? emptyState('Package not found', `No package "${name}" in your ${formatLabel(format)} repositories.`)
      : emptyState('Failed to load package', err.message);
  }
}

function wireTabs(container) {
  container.querySelectorAll('.tabs').forEach(group => {
    group.addEventListener('click', e => {
      const btn = e.target.closest('.tab-btn');
      if (!btn || !group.contains(btn)) return;
      const idx = btn.dataset.tab;
      // Scope to THIS group's direct tab buttons/panels (avoid nested groups).
      group.querySelectorAll(':scope > .tab-row > .tab-btn').forEach(b =>
        b.classList.toggle('active', b === btn));
      group.querySelectorAll(':scope > .tab-panel').forEach(p =>
        p.classList.toggle('hidden', p.dataset.panel !== idx));
    });
  });
}

function wireCopyButtons(container) {
  container.querySelectorAll('[data-copy]').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(btn.dataset.copy);
        const original = btn.textContent;
        btn.textContent = 'Copied!';
        setTimeout(() => { btn.textContent = original; }, 1500);
      } catch {
        // clipboard unavailable (e.g. non-secure context) — no-op
      }
    });
  });
}

// ─── Shared helpers ─────────────────────────────────────────────────────────

function getContent() {
  return document.getElementById('app-content');
}

function setPageTitle(title) {
  document.title = `${title} — Nexus Developer Portal`;
}

function attachCardHandlers(container) {
  container.querySelectorAll('[data-name]').forEach(card => {
    card.addEventListener('click', () => {
      const { format, name, group, repo } = card.dataset;
      const params = new URLSearchParams({ format: format || '', name: name || '' });
      if (group) params.set('group', group);
      if (repo)  params.set('repository', repo);
      navigate(`package?${params}`);
    });
    card.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); card.click(); }
    });
  });
}

function escAttr(str) {
  return String(str ?? '').replace(/&/g,'&amp;').replace(/"/g,'&quot;');
}
function escHTML(str) {
  return String(str ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ─── Bootstrap ───────────────────────────────────────────────────────────────

window.addEventListener('hashchange', route);
window.addEventListener('DOMContentLoaded', () => {
  if (!location.hash) location.hash = '#/';
  route();
});
