// Included on pages that use your data: go to the privacy notice first if you haven't agreed.
Api.Config.get()
  .then((c) => { if (!c.consentGiven) location.replace("consent.html"); })
  .catch(() => {});
