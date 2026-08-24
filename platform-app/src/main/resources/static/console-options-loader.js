(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.ConsoleOptionsLoader = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  async function loadOptions(loadProfiles, loadCases, delay) {
    let failure;
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        const [profiles, cases] = await Promise.all([loadProfiles(), loadCases()]);
        return { profiles, cases };
      } catch (error) {
        failure = error;
        if (attempt === 0) await delay();
      }
    }
    throw failure;
  }

  return { loadOptions };
}));
