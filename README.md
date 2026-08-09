# Love Doves

Love Doves is an open-source, private space for exactly two people. Messages
and photos are encrypted end to end, stored in an encrypted local vault, and
held by the relay only as short-lived ciphertext.

The first release targets Android 11 and newer. It is under active development
and has not yet received an independent security audit.

## Build

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME=/Users/ayalpani/Library/Android/sdk \
./gradlew assembleDebug
```

See `docs/architecture.md` and `docs/threat-model.md` before changing storage,
pairing, transport, or cryptographic code.

## License

GNU Affero General Public License v3.0. See `LICENSE`.
