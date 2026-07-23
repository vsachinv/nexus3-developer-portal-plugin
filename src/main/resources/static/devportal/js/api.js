/**
 * Developer Portal — API client
 * All calls go to /service/rest/devportal/api/*
 */

const API_BASE = '/service/rest/devportal/api';

async function apiFetch(path, options = {}) {
  const response = await fetch(API_BASE + path, {
    headers: { 'Accept': 'application/json', ...options.headers },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(`API error ${response.status}: ${text || response.statusText}`);
  }
  return response.json();
}

export const api = {
  /**
   * Search packages.
   * @param {string} query
   * @param {{format?: string, repository?: string, page?: number, pageSize?: number}} opts
   */
  search(query, opts = {}) {
    const params = new URLSearchParams({ q: query ?? '' });
    if (opts.format)     params.set('format', opts.format);
    if (opts.repository) params.set('repository', opts.repository);
    if (opts.page != null)     params.set('page', String(opts.page));
    if (opts.pageSize != null) params.set('pageSize', String(opts.pageSize));
    return apiFetch(`/search?${params}`);
  },

  /** List repositories, optionally filtered by format. */
  repositories(format) {
    const params = format ? `?format=${encodeURIComponent(format)}` : '';
    return apiFetch(`/repositories${params}`);
  },

  /** Get a single repository by name. */
  repository(name) {
    return apiFetch(`/repositories/${encodeURIComponent(name)}`);
  },

  /** Recently published packages. */
  recent(limit = 10) {
    return apiFetch(`/recent?limit=${limit}`);
  },

  /** Popular packages. */
  popular(limit = 10) {
    return apiFetch(`/popular?limit=${limit}`);
  },

  /**
   * Full detail for one package.
   * @param {{format: string, name: string, group?: string, repository?: string, version?: string}} opts
   */
  packageDetail({ format, name, group, repository, version }) {
    const params = new URLSearchParams({ format, name });
    if (group)      params.set('group', group);
    if (repository) params.set('repository', repository);
    if (version)    params.set('version', version);
    return apiFetch(`/package?${params}`);
  },
};
