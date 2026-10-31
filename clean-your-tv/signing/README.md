# Signing key

Both apps are signed with this one key, so each new build installs as an update over the last.
Without it, every GitHub Actions build signed the apps with a fresh random debug key, and Android
refused the updates.

The password is `cleanyourtv`. This key exists only so you can sideload your own builds; it is not
for publishing on Google Play. Anyone who has this repository can sign an APK that installs as an
update to these apps, so keep the repository private.
