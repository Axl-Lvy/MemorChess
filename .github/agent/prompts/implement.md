Implement the change described by the brief below in the repository in the current directory. Do not commit. Work only inside the current directory.

Work test-first: write the tests listed in the brief exactly as specified (same module, class, names and expected values), run them and see them fail, then change the production code until they pass. Follow the quoted rules. Reuse the listed helpers instead of re-implementing them. Touch only the listed files. Never weaken an expected value or threshold from the brief, and never change, disable or delete an existing assertion: change the production code instead.

Before finishing, run `./gradlew --offline` with: the compile tasks of the modules you changed (for example `:shared:compileKotlinJvm`), the JVM tests of every test class you added or changed (`:<module>:jvmTest --tests "*<Class>"`), and `ktfmtFormat`. Delete any scratch or backup files. Finish only when all of it passes.

When you are done, write `{"done": true, "notes": "<one paragraph: what you changed>"}` to the file `$AGENT_OUT/implement.json`.

Files in the touched directories:
{repo_map}

Brief:
{brief}
