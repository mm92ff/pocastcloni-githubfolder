package com.example.pocastcloni.release;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@LargeTest
@RunWith(AndroidJUnit4.class)
public final class ReleaseNavigationSmokeAndroidTest {
    private static final long TIMEOUT_MS = 30_000L;
    private static final String FEED_TITLE = "Release Smoke Podcast";
    private static final String EPISODE_TITLE = "Release Smoke Episode";
    private static final String BACKUP_FILE_NAME = "pocast_backup.json";

    private final Context targetContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private ReleaseFeedServer server;

    @Before
    public void setUp() throws IOException {
        server = new ReleaseFeedServer();
        device.executeShellCommand("rm -f /sdcard/Download/" + BACKUP_FILE_NAME);
    }

    @After
    public void tearDown() throws IOException {
        if (server != null) server.close();
    }

    @Test
    public void minifiedAppExercisesSettingsBackupFeedAndPlayer() {
        launchApp();
        waitForText("My Podcasts");

        openBottomDestination("Settings");
        waitForText("Design");
        addLocalReleaseFeed();

        openBottomDestination("Home");
        clickDescription("Cover image");
        waitForTextWithSummary(EPISODE_TITLE);
        clickDescription("Play/Pause");
        clickDescription("Open full player");
        waitForDescription("Close");
        clickDescription("Close");

        openBottomDestination("Settings");
        waitForText("Design");
        verifyBackupRoundTripEntryPoints();
    }

    private void verifyBackupRoundTripEntryPoints() {
        clickText("Data");
        waitForText("Export Backup");
        findTextWithScrolling("Import Backup");

        clickText("Export Backup");
        assertTrue(
                "Create-document picker did not open",
                device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT_MS)
        );
        UiObject2 save = device.wait(
                Until.findObject(By.res("android", "button1")),
                TIMEOUT_MS
        );
        assertNotNull("Document picker did not expose its save action", save);
        clickNodeOrClickableParent(save);

        waitForText("Export complete");
        dismissResultDialog("Export complete");

        clickNodeOrClickableParent(findTextWithScrolling("Import Backup"));
        assertTrue(
                "Open-document picker did not open",
                device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), TIMEOUT_MS)
        );
        clickText(BACKUP_FILE_NAME);
        confirmSelectedDocumentIfRequired();
        assertTrue(
                "Open-document picker did not return to the app",
                device.wait(Until.hasObject(By.pkg(targetContext.getPackageName())), TIMEOUT_MS)
        );
        waitForText("Import complete");
        dismissResultDialog("Import complete");
    }

    private void dismissResultDialog(String title) {
        device.pressBack();
        assertTrue(
                "Result dialog did not close: " + title,
                device.wait(Until.gone(By.text(title)), TIMEOUT_MS)
        );
    }

    private void confirmSelectedDocumentIfRequired() {
        if (device.wait(Until.hasObject(By.pkg(targetContext.getPackageName())), 5_000L)) return;

        device.pressEnter();
        assertTrue(
                "Selected document did not open",
                device.wait(Until.hasObject(By.pkg(targetContext.getPackageName())), TIMEOUT_MS)
        );
    }

    private void addLocalReleaseFeed() {
        clickText("Sync");
        waitForText("RSS Feed URL");

        UiObject2 urlInput = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")),
                TIMEOUT_MS
        );
        assertNotNull("RSS URL input was not exposed", urlInput);
        urlInput.click();
        urlInput.setText(server.url("/feed.xml"));
        dismissImeIfVisible();

        enableCheckbox("Allow legacy HTTP media");
        enableCheckbox("Allow local network feed");

        clickText("Add");
        boolean feedRequested = server.awaitFeedRequest();
        assertTrue(
                "Production feed request did not reach the local fixture. Visible text: "
                        + visibleTextSummary(),
                feedRequested
        );
        waitForText("Podcast added successfully");
    }

    private String visibleTextSummary() {
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        for (UiObject2 node : device.findObjects(By.text(Pattern.compile(".+")))) {
            try {
                String text = node.getText();
                if (text != null && !text.isBlank()) texts.add(text);
            } catch (StaleObjectException ignored) {
                // Compose may replace a snackbar node while the failure summary is collected.
            }
        }
        return String.join(" | ", texts);
    }

    private void enableCheckbox(String label) {
        for (int attempt = 0; attempt < 4; attempt++) {
            UiObject2 checkbox = findCheckboxNearestTo(label);
            if (checkbox.isChecked()) return;
            device.click(checkbox.getVisibleCenter().x, checkbox.getVisibleCenter().y);
            SystemClock.sleep(300L);
        }
        UiObject2 checkbox = findCheckboxNearestTo(label);
        assertTrue("Checkbox did not remain selected for " + label, checkbox.isChecked());
    }

    private UiObject2 findCheckboxNearestTo(String label) {
        UiObject2 labelNode = findTextWithScrolling(label);
        List<UiObject2> checkboxes = device.findObjects(By.checkable(true));
        assertTrue("No visible checkbox was exposed for " + label, !checkboxes.isEmpty());

        int labelCenterY = labelNode.getVisibleCenter().y;
        UiObject2 nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (UiObject2 checkbox : checkboxes) {
            int distance = Math.abs(checkbox.getVisibleCenter().y - labelCenterY);
            if (distance < nearestDistance) {
                nearest = checkbox;
                nearestDistance = distance;
            }
        }
        assertNotNull("Checkbox was not exposed for " + label, nearest);
        return nearest;
    }

    private void dismissImeIfVisible() {
        try {
            String state = device.executeShellCommand("dumpsys input_method");
            if (state.contains("mInputShown=true") || state.contains("mIsInputViewShown=true")) {
                device.pressBack();
                SystemClock.sleep(500L);
            }
        } catch (IOException error) {
            throw new AssertionError("Could not inspect the input method state", error);
        }
    }

    private UiObject2 findTextWithScrolling(String text) {
        for (int attempt = 0; attempt < 10; attempt++) {
            UiObject2 node = device.findObject(By.text(text));
            if (node != null) return node;
            scrollLargestContainerDown();
            SystemClock.sleep(300L);
        }
        return waitForTextWithSummary(text);
    }

    private void scrollLargestContainerDown() {
        UiObject2 largest = null;
        int largestHeight = 0;
        for (UiObject2 candidate : device.findObjects(By.scrollable(true))) {
            int height = candidate.getVisibleBounds().height();
            if (height > largestHeight) {
                largest = candidate;
                largestHeight = height;
            }
        }
        if (largest != null) {
            largest.scroll(Direction.DOWN, 0.75f);
            return;
        }
        device.swipe(
                device.getDisplayWidth() / 2,
                device.getDisplayHeight() * 70 / 100,
                device.getDisplayWidth() / 2,
                device.getDisplayHeight() * 35 / 100,
                20
        );
    }

    private void launchApp() {
        Intent launchIntent = targetContext.getPackageManager()
                .getLaunchIntentForPackage(targetContext.getPackageName());
        assertNotNull("Launcher activity is missing", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        targetContext.startActivity(launchIntent);
    }

    private void openBottomDestination(String label) {
        UiObject2 destination = device.wait(Until.findObject(By.text(label)), 2_000L);
        if (destination == null) {
            device.swipe(
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() * 88 / 100,
                    device.getDisplayWidth() / 2,
                    device.getDisplayHeight() * 55 / 100,
                    20
            );
            destination = waitForText(label);
        }
        clickNodeOrClickableParent(destination);
    }

    private void clickText(String text) {
        clickNodeOrClickableParent(waitForText(text));
    }

    private void clickDescription(String description) {
        clickNodeOrClickableParent(waitForDescription(description));
    }

    private UiObject2 waitForText(String text) {
        return waitFor(By.text(text), "text: " + text);
    }

    private UiObject2 waitForTextWithSummary(String text) {
        UiObject2 node = device.wait(Until.findObject(By.text(text)), TIMEOUT_MS);
        assertNotNull(
                "Timed out waiting for text: " + text + ". Visible text: " + visibleTextSummary(),
                node
        );
        return node;
    }

    private UiObject2 waitForDescription(String description) {
        return waitFor(By.desc(description), "description: " + description);
    }

    private UiObject2 waitFor(BySelector selector, String label) {
        UiObject2 node = device.wait(Until.findObject(selector), TIMEOUT_MS);
        assertNotNull("Timed out waiting for " + label, node);
        return node;
    }

    private static void clickNodeOrClickableParent(UiObject2 node) {
        UiObject2 target = node;
        while (target != null && !target.isClickable()) target = target.getParent();
        (target != null ? target : node).click();
    }

    private static final class ReleaseFeedServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final CountDownLatch feedRequested = new CountDownLatch(1);
        private volatile boolean closed;

        private ReleaseFeedServer() throws IOException {
            serverSocket = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            acceptThread = new Thread(this::acceptRequests, "release-feed-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private String url(String path) {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
        }

        private void acceptRequests() {
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread handler = new Thread(
                            () -> handleRequest(socket),
                            "release-feed-client"
                    );
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException error) {
                    if (!closed) throw new IllegalStateException(error);
                }
            }
        }

        private void handleRequest(Socket socket) {
            try (Socket connection = socket;
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII)
                 )) {
                String requestLine = reader.readLine();
                String header;
                do {
                    header = reader.readLine();
                } while (header != null && !header.isEmpty());

                String path = requestLine == null ? "" : requestLine.split(" ")[1];
                if ("/feed.xml".equals(path)) {
                    feedRequested.countDown();
                    writeResponse(
                            connection.getOutputStream(),
                            200,
                            "application/rss+xml",
                            feedXml().getBytes(StandardCharsets.UTF_8)
                    );
                } else if ("/episode.wav".equals(path)) {
                    writeResponse(
                            connection.getOutputStream(),
                            200,
                            "audio/wav",
                            silentWav()
                    );
                } else {
                    writeResponse(connection.getOutputStream(), 404, "text/plain", new byte[0]);
                }
            } catch (IOException ignored) {
                // Media clients may close a smoke-test stream after the UI assertion succeeds.
            }
        }

        private boolean awaitFeedRequest() {
            try {
                return feedRequested.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private static void writeResponse(
                OutputStream output,
                int status,
                String contentType,
                byte[] body
        ) throws IOException {
            String reason = status == 200 ? "OK" : "Not Found";
            String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                    + "Content-Type: " + contentType + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        }

        private String feedXml() {
            String baseUrl = url("/");
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">"
                    + "<channel><title>" + FEED_TITLE + "</title>"
                    + "<description>Release smoke feed</description>"
                    + "<image><url>" + baseUrl + "cover.png</url><title>" + FEED_TITLE
                    + "</title><link>" + baseUrl + "</link></image>"
                    + "<item><guid>release-smoke-guid</guid><title>" + EPISODE_TITLE + "</title>"
                    + "<description>Release smoke episode</description>"
                    + "<pubDate>Mon, 13 Jul 2026 08:00:00 GMT</pubDate>"
                    + "<enclosure url=\"" + baseUrl
                    + "episode.wav\" type=\"audio/wav\" length=\"441044\"/>"
                    + "<itunes:duration>5</itunes:duration></item></channel></rss>";
        }

        private static byte[] silentWav() {
            int sampleRate = 44_100;
            int dataSize = sampleRate * 2 * 5;
            ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            buffer.putInt(36 + dataSize);
            buffer.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            buffer.putInt(16);
            buffer.putShort((short) 1);
            buffer.putShort((short) 1);
            buffer.putInt(sampleRate);
            buffer.putInt(sampleRate * 2);
            buffer.putShort((short) 2);
            buffer.putShort((short) 16);
            buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            buffer.putInt(dataSize);
            return buffer.array();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
        }
    }
}
