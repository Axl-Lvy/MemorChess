# Commit Message Instructions

- Do not use conventional commit message format.
- The commit message should have a short description (50 characters or less) followed by a blank line and then an optional longer description.
- The long description should provide additional context and details about the change.
    - Explain why the change was made.
    - Describe what is being used and why.
    - Include any relevant information that might be useful for understanding the change in the future.
    - Reference any related issues or pull requests at the end of the long description.
- Only add a long description if the change is significant or complex and cannot be resumed in few words.

## Example

### Commit Message Example

```
Add user authentication

Added user authentication using JWT. This includes login, registration, and token verification endpoints.

- Implemented JWT-based authentication.
- Added login and registration endpoints.
- Added middleware for token verification.
```

### Small Change Examples

```
Changed tile color
```

```
Refactored `BoardGrid`
```