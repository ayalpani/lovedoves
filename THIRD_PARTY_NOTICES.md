# Third-party notices

Love Doves uses open-source libraries under their respective licenses.

- libsignal, Signal Messenger LLC — AGPL-3.0
- SQLCipher for Android, Zetetic LLC — BSD-style license
- BIP-0039 English word list, Bitcoin BIPs contributors — MIT license
- ZXing — Apache-2.0
- Lucide icons, Lucide Contributors — ISC license (with Feather-derived icons
  under MIT)
- NudeNet 320n model, notAI-tech — AGPL-3.0. Love Doves bundles a local FP16
  LiteRT conversion of the [v3.4 `320n.pt`
  model](https://github.com/notAI-tech/NudeNet/releases/tag/v3.4-weights),
  converted at 320 px with Ultralytics 8.3.220 and TensorFlow 2.19.0. Source SHA-256:
  `1d25e219d536dcd6994651020d3c7cba642d13990e6eef934ed7a8ba650fb582`;
  bundled model SHA-256:
  `f9fd78bbadbfa87788b7a3b38ebd3c292b51c04763e8697e707e37972c484cce`.
- LiteRT, Google AI Edge Authors — Apache-2.0
- AndroidX, ML Kit, Protocol Buffers and Firebase client libraries — their
  published open-source terms

The BIP-0039 word list is used only to turn a cryptographic pairing fingerprint
into six human-comparable words. It is not used to derive encryption keys.
