# Signing

Android installs a new build as an update only if it is signed with the same key as the
installed copy. Signing keys are credentials, so none is committed to this repository.

- **Default:** the build workflow keeps the standard Android debug key between runs (in the
  GitHub Actions cache), so new builds install as updates. GitHub drops a cache that goes
  unused for 7 days. If that happens, the key changes:
  - **Clean Home on the TV:** "Update Clean Home on the TV" in the phone app detects this and
    reinstalls it. Only Clean Home's hidden-apps list and weather city are lost.
  - **The phone app:** uninstall the old one before installing the new one.
- **Optional, fully stable:** create your own key and add it as repository secrets.
  1. Make the key: `keytool -genkeypair -keystore clean-your-tv.keystore -alias cleanyourtv
     -keyalg RSA -keysize 2048 -validity 36500`.
  2. Base64-encode the file and save it as the secret `CYT_KEYSTORE_BASE64`.
  3. Save its password as the secret `CYT_KEYSTORE_PASSWORD`.

  The workflow then writes the key to `signing/clean-your-tv.keystore` and signs both apps with it.
