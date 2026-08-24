(() => {
  function createListQuery(value = {}) {
    return { page: value.page || 0, size: value.size || 20, filters: { ...(value.filters || {}) } };
  }

  function applyFilters(query, filters) {
    return { page: 0, size: query.size, filters: { ...filters } };
  }

  function toSearchParams(query) {
    const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
    Object.entries(query.filters).forEach(([key, value]) => {
      if (value !== '' && value != null) params.set(key, String(value));
    });
    return params;
  }

  const api = { createListQuery, applyFilters, toSearchParams };
  if (typeof module !== 'undefined') module.exports = api;
  if (typeof window !== 'undefined') window.ConsoleListQuery = api;
})();
