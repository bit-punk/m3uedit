package org.bitpunk.m3uedit.plugins.sftp;

import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
import org.bitpunk.m3uedit.plugins.sftp.SftpUserInteraction.OverwriteDecision;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.util.HeadlessMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlessSftpUserInteractionTest {

    @AfterEach
    void leaveHeadlessMode() {
        HeadlessMode.setActive(false);
    }

    private static HeadlessSftpUserInteraction withEnvironment(Map<String, String> environment) {
        return new HeadlessSftpUserInteraction(environment::get);
    }

    @Test
    void testThePasswordIsTakenFromTheEnvironment() {
        SftpConnection connection = new SftpConnection();
        HeadlessSftpUserInteraction interaction = withEnvironment(Map.of(HeadlessSftpUserInteraction.PASSWORD_VARIABLE, "secret"));

        assertThat(interaction.ensurePassword(connection)).isTrue();
        assertThat(connection.getPassword()).isEqualTo("secret");
    }

    private static SftpConnection connection(String userName, String hostName) {
        SftpConnection connection = new SftpConnection();
        connection.setUserName(userName);
        connection.setHostName(hostName);
        return connection;
    }

    @Test
    void testEachServerCanHaveItsOwnPassword() {
        HeadlessSftpUserInteraction interaction = withEnvironment(Map.of(
                "M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22", "router",
                "M3UEDIT_SFTP_PASSWORD_MEDIA_NAS_LOCAL", "nas",
                HeadlessSftpUserInteraction.PASSWORD_VARIABLE, "default"));
        SftpConnection router = connection("root", "192.168.178.22");
        SftpConnection nas = connection("media", "nas.local");
        SftpConnection other = connection("pi", "raspberrypi");

        assertThat(interaction.ensurePassword(router) && interaction.ensurePassword(nas)
                && interaction.ensurePassword(other)).isTrue();

        assertThat(router.getPassword()).isEqualTo("router");
        assertThat(nas.getPassword()).isEqualTo("nas");
        // the general variable is used for all other servers
        assertThat(other.getPassword()).isEqualTo("default");
    }

    @Test
    void testTheNameOfTheVariableIsValidInEveryShell() {
        assertThat(HeadlessSftpUserInteraction.passwordVariable("root", "192.168.178.22"))
                .isEqualTo("M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22");
        assertThat(HeadlessSftpUserInteraction.passwordVariable("my-user", "nas.fritz.box"))
                .isEqualTo("M3UEDIT_SFTP_PASSWORD_MY_USER_NAS_FRITZ_BOX");
        assertThat(HeadlessSftpUserInteraction.passwordVariable("admin", "fe80::1"))
                .matches("[A-Z0-9_]+");
    }

    @Test
    void testWithoutThePasswordVariableThereIsNoPassword() {
        SftpConnection connection = new SftpConnection();

        assertThat(withEnvironment(Map.of()).ensurePassword(connection)).isFalse();
        assertThat(withEnvironment(Map.of(HeadlessSftpUserInteraction.PASSWORD_VARIABLE, "")).ensurePassword(connection))
                .isFalse();
        assertThat(connection.getPassword()).isNullOrEmpty();
    }

    @Test
    void testNoQuestionsAreAsked() {
        HeadlessSftpUserInteraction interaction = withEnvironment(Map.of());

        assertThat(interaction.promptYesNo("The authenticity of host 'nas' can't be established.")).isFalse();
        assertThat(interaction.confirmOverwrite("root@nas:22/tv.m3u", "Existing file: 10 bytes"))
                .isEqualTo(OverwriteDecision.OVERWRITE);
        // only logged, no dialog
        interaction.showMessage("hello");
    }

    @Test
    void testTheHeadlessInteractionIsUsedInHeadlessMode() {
        PluginContext context = new DefaultPluginContext(SftpInput.class);
        assertThat(SftpUserInteraction.create(context)).isNotInstanceOf(HeadlessSftpUserInteraction.class);

        HeadlessMode.setActive(true);

        assertThat(SftpUserInteraction.create(context)).isInstanceOf(HeadlessSftpUserInteraction.class);
    }
}
