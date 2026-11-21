package org.bitpunk.m3uedit.controller;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import org.bitpunk.m3uedit.config.Configuration;
import org.bitpunk.m3uedit.config.Configuration.Collected;
import org.bitpunk.m3uedit.config.Language;
import org.bitpunk.m3uedit.config.RecentProjects;
import org.bitpunk.m3uedit.config.RecentProjectsConfig;
import org.bitpunk.m3uedit.config.Settings;
import org.bitpunk.m3uedit.config.StartupMode;
import org.bitpunk.m3uedit.config.Theme;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.player.ExternalPlayer;
import org.bitpunk.m3uedit.player.ExternalPlayerConfig;
import org.bitpunk.m3uedit.plugins.input.fritzbox.RecentFritzBoxConnections;
import org.bitpunk.m3uedit.plugins.sftp.RecentSftpServers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationDialogTest {

    @TempDir
    Path tempDir;
    private Configuration configuration;
    private RecentProjects recentProjects;

    @BeforeAll
    static void initToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already started
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        ExternalPlayer player = new ExternalPlayer(new YamlConfigManager<>(tempDir.resolve("external_player.yaml"),
                ExternalPlayerConfig.class, ExternalPlayerConfig::new));
        player.saveConfig(new ExternalPlayerConfig("/usr/bin/mpv", List.of("--fs", "--title=My TV"), false));
        recentProjects = new RecentProjects(new YamlConfigManager<>(tempDir.resolve("recent_projects.yaml"),
                RecentProjectsConfig.class, RecentProjectsConfig::new), Clock.systemUTC());
        recentProjects.setMaxEntries(7);
        recentProjects.add(Files.writeString(tempDir.resolve("tv.m3uproj"), "name: tv"));
        Settings settings = new Settings(tempDir.resolve("settings.yaml"));
        settings.setStartup(StartupMode.REOPEN_PROJECTS);
        configuration = new Configuration(player, recentProjects, new RecentSftpServers(tempDir.resolve("sftp.yaml")),
                new RecentFritzBoxConnections(tempDir.resolve("fritzbox.yaml")),
                Files.createDirectories(tempDir.resolve("epg")), settings);
    }

    private interface DialogAction<T> {
        T run(ConfigurationDialog dialog);
    }

    private static <T> T withDialog(Configuration configuration, DialogAction<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.run(new ConfigurationDialog(configuration)));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test
    void testTheDialogShowsTheCurrentSettings() throws Exception {
        List<Object> shown = withDialog(configuration, dialog -> List.of(dialog.getPlayerCommandField().getText(),
                dialog.getPlayerArgumentsField().getText(), dialog.getPassVlcOptionsCheckBox().isSelected(),
                dialog.getRecentProjectsSpinner().getValue(), dialog.getReopenProjectsRadioButton().isSelected(),
                dialog.getCollectedLabels().get(Collected.RECENT_PROJECTS).getText(),
                dialog.getClearButtons().get(Collected.RECENT_PROJECTS).isDisable(),
                dialog.getCollectedLabels().get(Collected.SFTP_SERVERS).getText(),
                dialog.getClearButtons().get(Collected.SFTP_SERVERS).isDisable()));

        assertThat(shown).containsExactly("/usr/bin/mpv", "--fs \"--title=My TV\"", false, 7, true, "1 entry", false,
                "empty", true);
    }

    @Test
    void testOkReturnsTheSettingsEntered() throws Exception {
        ConfigurationDialog.Result result = withDialog(configuration, dialog -> {
            dialog.getPlayerCommandField().setText("vlc");
            dialog.getPlayerArgumentsField().setText("--no-osd  \"--video-title=Das Erste\"");
            dialog.getPassVlcOptionsCheckBox().setSelected(true);
            // typed without pressing Enter
            dialog.getRecentProjectsSpinner().getEditor().setText("3");
            dialog.getStartScreenRadioButton().setSelected(true);
            dialog.getLanguageComboBox().setValue(Language.GERMAN);
            dialog.getThemeComboBox().setValue(Theme.DARK);
            dialog.getClearButtons().get(Collected.RECENT_PROJECTS).fire();
            return dialog.getResultConverter().call(dialog.getOkButtonType());
        });

        assertThat(result.values()).isEqualTo(new Configuration.Values("vlc",
                List.of("--no-osd", "--video-title=Das Erste"), true, 3, StartupMode.START_SCREEN, Language.GERMAN,
                Theme.DARK));
        assertThat(result.toClear()).containsExactly(Collected.RECENT_PROJECTS);
    }

    @Test
    void testAListToClearIsOnlyMarkedUntilOk() throws Exception {
        List<Object> state = withDialog(configuration, dialog -> {
            dialog.getClearButtons().get(Collected.RECENT_PROJECTS).fire();
            return List.of(dialog.getCollectedLabels().get(Collected.RECENT_PROJECTS).getText(),
                    dialog.getClearButtons().get(Collected.RECENT_PROJECTS).isDisable(),
                    dialog.getResultConverter().call(ButtonType.CANCEL) == null);
        });

        assertThat(state).containsExactly("cleared when OK is pressed", true, true);
        assertThat(recentProjects.getProjects()).as("cancel clears nothing").hasSize(1);
    }

    @Test
    void testTheNumberOfRecentProjectsIsLimited() throws Exception {
        ConfigurationDialog.Result result = withDialog(configuration, dialog -> {
            dialog.getRecentProjectsSpinner().getEditor().setText("500");
            return dialog.getResultConverter().call(dialog.getOkButtonType());
        });

        assertThat(result.values().maxRecentProjects()).isEqualTo(ConfigurationDialog.MAX_RECENT_PROJECTS);
        assertThat(result.toClear()).isEqualTo(Set.of());
    }
}
