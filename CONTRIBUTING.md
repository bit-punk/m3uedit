# Contributing to M3UEditor

Thank you for your interest and for taking the time to contribute! Bug reports, ideas and pull requests are welcome.

## Reporting bugs and proposing features

Create an [issue](https://github.com/bit-punk/m3uedit/issues) (if it does not exist already). For bugs, please
describe what you did, what you expected and what happened instead, and add the version and your operating system.
Security issues should not be reported in a public issue, see [SECURITY.md](SECURITY.md).

## Contributing code

1. **Fork the repository** and clone your fork.
2. **Create a branch** from `develop`, named after the kind of change:
   * `feature/<name>` for new features and improvements
   * `fix/<name>` for bug fixes
   * `chore/<name>` for maintenance, e.g. cleanups or build changes

   The prefix labels the pull request automatically, which sorts it into the release notes.
3. **Make your changes** and add tests for them. The tests are run by `./mvnw package` (JDK 25+ is needed); some of
   them show windows, so on a machine without display use `xvfb-run ./mvnw package`.
4. **Push** the branch to your fork.
5. **Open a pull request** against `develop`. Its title is taken over into the release notes as it is, so please
   describe the change for users (e.g. "Show the channel logos in the playlist table" rather than "Feature/logos"),
   and explain the change in the description.

The build and the tests run automatically for each pull request, please make sure they succeed.

## Code of Conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By participating you are expected to uphold it.

## Questions

If you have any questions, create an issue and we will do our best to answer it as soon as we can.
