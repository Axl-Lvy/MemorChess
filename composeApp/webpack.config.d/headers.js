if (config.devServer) {
  config.devServer.headers = Object.assign(config.devServer.headers || {}, {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "credentialless"
  });
}
