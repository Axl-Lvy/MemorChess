config.set({
  browserNoActivityTimeout: 120000,
  browserDisconnectTimeout: 30000,
  // Tolerate transient browser disconnects on a loaded CI runner; default tolerance of 0 fails the
  // whole run on a single missed heartbeat with zero test failures (issue #228).
  browserDisconnectTolerance: 3,
  pingTimeout: 60000,
  client: {
    mocha: {
      timeout: 60000
    }
  },
  customHeaders: [
    {
      match: '.*',
      name: 'Cross-Origin-Opener-Policy',
      value: 'same-origin'
    },
    {
      match: '.*',
      name: 'Cross-Origin-Embedder-Policy',
      value: 'credentialless'
    }
  ]
});
