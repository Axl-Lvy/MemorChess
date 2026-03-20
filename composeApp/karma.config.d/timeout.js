config.set({
  browserNoActivityTimeout: 120000,
  browserDisconnectTimeout: 30000,
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
