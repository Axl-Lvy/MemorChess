if (config.devServer) {
  config.devServer.headers = Object.assign(config.devServer.headers || {}, {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "credentialless"
  });
  // Redirect callback for the sync OIDC flow: served by :server in production, but the webpack
  // dev server needs its own rewrite to reach index.html for the same path locally.
  config.devServer.historyApiFallback = {
    rewrites: [{ from: /^\/sync-oauth-callback/, to: "/index.html" }]
  };
}
