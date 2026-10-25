package dev.kizuna.playeranimation.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class EquipVisibilityClientRunner implements ClientModInitializer {
    private boolean finished;

    @Override
    public void onInitializeClient() {
        // 进入真实集成世界后在客户端主线程执行测试，结束后正常关闭客户端和服务器。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished || client.world == null || client.player == null) {
                return;
            }
            finished = true;
            runTests();
            client.scheduleStop();
        });
    }

    private static void runTests() {
        // 显式发现回归类，空测试集和中止的测试不能被当作成功。
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(EquipVisibilityClientTest.class))
                .build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        var console = new PrintWriter(System.out, true /* autoFlush */);
        summary.printTo(console);
        summary.printFailuresTo(console);
        Path report = Path.of(System.getProperty("kpa.clientTest.report"));
        try (var writer = new PrintWriter(Files.newBufferedWriter(report))) {
            boolean success = summary.getTestsFoundCount() > 0
                    && summary.getTestsSucceededCount() == summary.getTestsFoundCount()
                    && summary.getTotalFailureCount() == 0;
            writer.println("success=" + success);
            summary.printTo(writer);
            summary.printFailuresTo(writer);
        } catch (IOException failure) {
            throw new IllegalStateException("无法写入客户端测试报告", failure);
        }
    }
}
