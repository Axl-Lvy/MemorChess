config.set({
  browserNoActivityTimeout: 120000,
  browserDisconnectTimeout: 30000,
  // The single wasm thread can block the browser event loop (PGN parse, graph build, GC) long
  // enough to miss a Karma heartbeat on a loaded CI runner. With the default tolerance of 0 a
  // single missed ping fails the whole run with zero test failures (issue #228), so tolerate a
  // few transient disconnects and give the socket more time before declaring the browser dead.
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
