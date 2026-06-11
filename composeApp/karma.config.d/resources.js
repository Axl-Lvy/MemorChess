// Serve compose resources (strings, drawables, fonts) to browser tests, following the setup used
// by JetBrains in compose-multiplatform's own resources library (components/resources/library/
// karma.config.d/wasm/config.js). Without this the resource requests issued by the test bundle
// have nothing to resolve against and every stringResource/painterResource stays unloaded.
// test_setup.js (from src/wasmJsTest/resources) additionally switches the resource reader to a
// blocking XMLHttpRequest so reads complete inside the test framework's synchronous waits.

const path = require("path");

const basePath = config.basePath;
const projectPath = path.resolve(basePath, "..", "..", "..", "..");
const generatedAssetsPath = path.resolve(projectPath, "build", "karma-webpack-out");

config.proxies["/"] = path.resolve(basePath, "kotlin");

config.files = [
    {pattern: path.resolve(generatedAssetsPath, "**/*"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.png"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.cvr"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.otf"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.gif"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.ttf"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.txt"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.json"), included: false, served: true, watched: false},
    {pattern: path.resolve(basePath, "kotlin", "**/*.xml"), included: false, served: true, watched: false},
    path.resolve(basePath, "kotlin", "test_setup.js"),
].concat(config.files);

function KarmaWebpackOutputFramework(config) {
    // This controller is instantiated and set during the preprocessor phase.
    const controller = config.__karmaWebpackController;

    // only if webpack has instantiated its controller
    if (!controller) {
        console.warn(
            "Webpack has not instantiated controller yet.\n" +
            "Check if you have enabled webpack preprocessor and framework before this framework"
        );
        return;
    }

    config.files.push({
        pattern: `${controller.outputPath}/**/*`,
        included: false,
        served: true,
        watched: false
    });
}

const KarmaWebpackOutputPlugin = {
    'framework:webpack-output': ['factory', KarmaWebpackOutputFramework],
};

config.plugins.push(KarmaWebpackOutputPlugin);
config.frameworks.push("webpack-output");
