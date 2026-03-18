config.set({
  client: {
    mocha: {
      timeout: 30000
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
