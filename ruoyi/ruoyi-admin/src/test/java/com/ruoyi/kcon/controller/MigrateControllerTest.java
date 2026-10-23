package com.ruoyi.kcon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.service.SensitiveOperationGuard;

/**
 * 数据迁移测试：恢复的密码优先语义、文件前置校验与危险 SQL 拦截规则。
 *
 * <p>导出接口依赖容器内的 mysqldump 外部进程，无法在纯单元测试中执行，
 * 这里覆盖其库名解析逻辑；恢复只覆盖不会真正触发 mysql 进程的分支。
 */
@ExtendWith(MockitoExtension.class)
class MigrateControllerTest
{
    @Mock
    private SensitiveOperationGuard sensitiveOperationGuard;

    private MigrateController controller;

    @BeforeEach
    void setUp()
    {
        controller = new MigrateController();
        ReflectionTestUtils.setField(controller, "sensitiveOperationGuard", sensitiveOperationGuard);
        ReflectionTestUtils.setField(controller, "jdbcUrl",
                "jdbc:mysql://mysql:3306/kcon_lucky?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    }

    @Test
    @DisplayName("恢复：密码错误且没有文件时，提示密码问题而不是文件问题")
    void restoreVerifiesPasswordBeforeCheckingFile()
    {
        doThrow(new ServiceException("管理员密码不正确，还可尝试 4 次"))
                .when(sensitiveOperationGuard).verifyCurrentUserPassword("wrong", "数据恢复");

        assertThatThrownBy(() -> controller.restore(null, "wrong"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("管理员密码不正确，还可尝试 4 次");

        verify(sensitiveOperationGuard).verifyCurrentUserPassword("wrong", "数据恢复");
    }

    @Test
    @DisplayName("恢复：空密码同样先走密码校验，提示请输入密码")
    void restoreRejectsEmptyPasswordFirst()
    {
        doThrow(new ServiceException("请输入数据恢复所需的管理员密码"))
                .when(sensitiveOperationGuard).verifyCurrentUserPassword(null, "数据恢复");

        assertThatThrownBy(() -> controller.restore(null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessage("请输入数据恢复所需的管理员密码");

        verify(sensitiveOperationGuard).verifyCurrentUserPassword(null, "数据恢复");
    }

    @Test
    @DisplayName("恢复：密码通过后缺文件才提示请选择 .sql 文件")
    void restoreRejectsMissingFileAfterPasswordPasses() throws Exception
    {
        AjaxResult result = controller.restore(null, "pwd123456");

        assertThat(result.get(AjaxResult.CODE_TAG)).isEqualTo(500);
        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("请选择要恢复的 .sql 文件");
        verify(sensitiveOperationGuard).verifyCurrentUserPassword("pwd123456", "数据恢复");
    }

    @Test
    @DisplayName("恢复：超过 20MB 的文件被拒绝")
    void restoreRejectsOversizeFile() throws Exception
    {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        AjaxResult result = controller.restore(file, "pwd123456");

        assertThat(result.get(AjaxResult.MSG_TAG))
                .isEqualTo("文件超过 20MB 上限，请确认是否为有效的数据库备份");
    }

    @Test
    @DisplayName("恢复：非 .sql 后缀的文件被拒绝")
    void restoreRejectsNonSqlFile() throws Exception
    {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("backup.zip");

        AjaxResult result = controller.restore(file, "pwd123456");

        assertThat(result.get(AjaxResult.MSG_TAG)).isEqualTo("只支持 .sql 文件");
    }

    @Test
    @DisplayName("危险 SQL 拦截：行首 DROP/TRUNCATE/DELETE/GRANT/INTO OUTFILE 被拦，正常 INSERT 放行")
    void detectsDangerousStatements(@TempDir Path tempDir) throws Exception
    {
        assertThat(detect(tempDir, "DROP TABLE kcon_attendee;\n")).contains("DROP TABLE");
        assertThat(detect(tempDir, "  drop table kcon_winner;\n")).contains("drop table");
        assertThat(detect(tempDir, "DROP TABLE IF EXISTS kcon_attendee;\n")).isNull();
        assertThat(detect(tempDir, "DROP DATABASE IF EXISTS kcon_lucky;\n")).isNull();
        assertThat(detect(tempDir, "TRUNCATE TABLE kcon_attendee;\n")).contains("TRUNCATE");
        assertThat(detect(tempDir, "DELETE FROM kcon_attendee;\n")).contains("DELETE");
        assertThat(detect(tempDir, "GRANT ALL PRIVILEGES ON *.* TO 'x'@'%';\n")).contains("GRANT");
        assertThat(detect(tempDir, "SELECT * FROM kcon_attendee INTO OUTFILE '/tmp/a.txt';\n"))
                .contains("OUTFILE");
        assertThat(detect(tempDir, "INSERT INTO kcon_attendee (name) VALUES ('张三');\n")).isNull();
    }

    @Test
    @DisplayName("危险 SQL 拦截：数据行里出现关键字但不在行首时不误伤（自己导出的备份能恢复）")
    void ignoresDangerousKeywordsInsideData(@TempDir Path tempDir) throws Exception
    {
        String sql = "INSERT INTO sys_oper_log (oper_param) VALUES "
                + "('已拒绝执行：DROP DATABASE x，TRUNCATE TABLE y');\n";

        assertThat(detect(tempDir, sql)).isNull();
    }

    @Test
    @DisplayName("库名解析：先截掉查询串再取最后一段路径")
    void parsesDatabaseNameFromJdbcUrl()
    {
        assertThat(invokeParse("jdbc:mysql://mysql:3306/kcon_lucky?serverTimezone=Asia/Shanghai&x=1"))
                .isEqualTo("kcon_lucky");
        assertThat(invokeParse("jdbc:mysql://mysql:3306/kcon_lucky")).isEqualTo("kcon_lucky");
    }

    @Test
    @DisplayName("库名解析：URL 缺失或库名为空时拒绝")
    void rejectsInvalidJdbcUrl()
    {
        assertThatThrownBy(() -> invokeParse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据源 URL 未配置");
        assertThatThrownBy(() -> invokeParse("jdbc:mysql://mysql:3306/"))
                .isInstanceOf(IllegalStateException.class);
    }

    private String detect(Path dir, String sql) throws Exception
    {
        Path file = Files.createTempFile(dir, "restore_", ".sql");
        Files.writeString(file, sql, StandardCharsets.UTF_8);
        return ReflectionTestUtils.invokeMethod(controller, "findDangerousStatement", file);
    }

    private String invokeParse(String url)
    {
        return ReflectionTestUtils.invokeMethod(controller, "parseDatabaseName", url);
    }
}
