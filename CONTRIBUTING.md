# Contributing

Thanks for helping. PULSE is small on purpose, so a few rules keep it reliable.

## Bug reports

Use the bug report template. The most useful thing you can attach is **real output** from
the console (Copy or Share button), plus your device, Android version and Shizuku version.

## Pull requests

- One topic per pull request.
- Do not change toolchain versions (AGP, Kotlin, Gradle, SDK, Shizuku API) in a feature PR.
- New parsers are pure Kotlin with a unit test built from real device output.
- Every command that changes something must be followed by a read-back; report "applied"
  only if the read-back matches.
- Package names are validated and quoted with `ShellQuoting.quote` before reaching the shell.
- Shell text in the UI uses `LtrMonoText`.
- Never write a star followed by a slash inside a block comment (it ends the comment).
- CI must be green. It runs unit tests, lint and assembles the debug APK.

## Root features

Anything that needs root stays locked until it has been tested on a real rooted device.
A PR adding a root feature must include device output proving it works.

## License

By contributing you agree that your contribution is licensed under the MIT license.
