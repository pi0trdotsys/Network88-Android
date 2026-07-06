<h2>📱 Network88 — Android Network Speed Test (Java)</h2>

<p>Network88 is a lightweight network utility app that measures your connection quality and keeps a
local history of every test. It runs a proper sequential speed test, reports live progress, and stores
each result as JSON on the device.</p>

<h3>✨ Features</h3>

<ul>
  <li>📥 Measure <b>download speed</b> (Mbps)</li>
  <li>📤 Measure <b>upload speed</b> (Mbps)</li>
  <li>📡 Send a <b>ping</b> and read the round-trip time in ms</li>
  <li>🌐 Show your <b>IP address</b> and <b>subnet mask</b></li>
  <li>🕘 <b>Measurement history</b> persisted locally as JSON, viewable in-app</li>
  <li>↺ Restores your <b>last result</b> on launch so the screen is never empty</li>
  <li>🔔 Notification with the result when a test completes</li>
</ul>

<h3>🧠 How the speed test works</h3>

<p>Download and upload run <b>sequentially</b> (never at the same time) so they don't compete for the
same link and skew each other's numbers. Each phase runs on its own socket, and the next phase only
starts once the previous one has actually completed — coordinated with a <code>CountDownLatch</code>
instead of a fixed timer. The reported rate is the final figure from the completion callback, not a
mid-test snapshot. Speed tests are powered by
<a href="https://github.com/bertrandmartel/speed-test-lib">jSpeedtest</a>.</p>

<h3>🗂️ Measurement history (JSON)</h3>

<p>Every completed test is appended to <code>measurements.json</code> in the app's private storage
(newest first, capped at the 100 most recent entries). Each entry stores the timestamp, download and
upload rates, ping, IP address and subnet mask:</p>

<pre>
[
  {
    "timestamp": 1751800200000,
    "downloadMbps": 94.2,
    "uploadMbps": 12.7,
    "pingMs": 24,
    "ipAddress": "192.168.0.14",
    "subnetMask": "255.255.255.0"
  }
]
</pre>

<h3>🏗️ Tech stack</h3>

<ul>
  <li>Java, minSdk 21 / targetSdk 31</li>
  <li>Material Components (dark theme, cards, circular progress)</li>
  <li>AndroidX (AppCompat, ConstraintLayout, RecyclerView), View Binding</li>
  <li><code>org.json</code> for local history persistence</li>
</ul>

<h3>🔨 Build</h3>

<pre>
# Requires JDK 17 and the Android SDK
./gradlew :app:assembleDebug
</pre>

<h3>📸 Screenshots</h3>
<p>
  <img src="docs/screenshots/home.png" width="240px" alt="Home screen showing the latest download, upload, ping, IP and subnet results">
  &nbsp;
  <img src="docs/screenshots/measuring.png" width="240px" alt="Speed test in progress with live download speed and a progress indicator">
  &nbsp;
  <img src="docs/screenshots/history.png" width="240px" alt="Measurement history screen listing previous speed tests">
</p>

<h2>👇 APK Download</h2>
<p>Download the latest APK from the <a href="https://github.com/pi0trdotsys/Network88-Android/releases">Release page</a> (requires Android 7.0 or above).</p>
