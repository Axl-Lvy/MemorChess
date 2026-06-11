// Compose for Web ships multi-megabyte wasm binaries (skiko.wasm alone is ~8 MiB),
// so webpack's 244 KiB asset-size recommendation can never be met.
config.performance = Object.assign(config.performance || {}, {
  hints: false
});
