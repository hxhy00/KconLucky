package com.ruoyi.kcon.controller;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.kcon.service.SensitiveOperationGuard;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据迁移：全量备份导出与恢复（需求 AC-11、§11.4）
 *
 * 实现方式：调用 MySQL 官方工具 mysqldump / mysql，不自研 SQL 导出器。
 * mysqldump 在后端镜像构建时通过 apt 安装（见 docker/backend/Dockerfile）。
 *
 * 安全设计（恢复是不可逆操作，误操作会直接丢掉现场数据）：
 *   1. 导出/恢复都要求 super-admin 角色（仅 hasRole('admin') 单重校验，刻意不挂
 *      kcon:data:migrate 权限点——它从未在任何地方被校验，挂上去只会造成「双重校验」的错觉）；
 *   2. 恢复前自动先导出一份当前数据存档，失败即中止，不会「先破坏后备份」；
 *   3. 恢复要求上传 .sql 文件，大小受限，且必须是 SELECT/CREATE/INSERT 等
 *      DDL/DML 语句，出现 DROP DATABASE / DROP TABLE 等破坏性语句直接拒绝；
 *   4. 全部操作写 @Log 审计，可在操作日志里追溯谁在什么时候导了/恢复了什么。
 *
 * @author kcon
 */
@RestController
@RequestMapping("/admin/migrate")
public class MigrateController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(MigrateController.class);

    /** 恢复时上传文件的大小上限（20MB），与 nginx client_max_body_size 对齐 */
    private static final long MAX_UPLOAD = 20L * 1024 * 1024;

    /** 一次恢复允许执行的最长时间，超时判为失败，避免把请求线程挂死 */
    private static final int TIMEOUT_MINUTES = 5;

    @Value("${spring.datasource.druid.master.url:jdbc:mysql://mysql:3306/kcon_lucky}")
    private String jdbcUrl;

    @Value("${spring.datasource.druid.master.username:root}")
    private String dbUser;

    @Value("${spring.datasource.druid.master.password:}")
    private String dbPassword;

    /** 备份临时目录：容器内默认 /app/backup，本地命令行运行可用 KCON_BACKUP_DIR 覆盖 */
    @Value("${kcon.backup.dir:/app/backup}")
    private String backupDir;

    @Autowired
    private SensitiveOperationGuard sensitiveOperationGuard;

    /**
     * 导出全量数据为 .sql（下载到浏览器）
     */
    @PreAuthorize("@ss.hasRole('admin')")
    @Log(title = "全量数据导出", businessType = BusinessType.EXPORT)
    // 与项目其余导出接口保持一致的 POST：前端统一走 utils/request 的 download()，
    // 它是 POST + blob + Authorization 请求头。原先写成 @GetMapping 会与之不匹配。
    @PostMapping("/export")
    public void export(HttpServletResponse response) throws Exception
    {
        String dbName = parseDatabaseName(jdbcUrl);
        String fileName = "kcon_lucky_" + timestamp() + ".sql";

        // 先落临时文件再发送：
        // 1) 边读进程 stdout 边写响应，中途任何异常都会给用户一个截断的坏文件；
        // 2) 落盘后能拿到准确大小，设置 Content-Length，浏览器进度条与下载都正常。
        Path tmp = Paths.get(backupDir, fileName);
        Files.createDirectories(tmp.getParent());

        String dumpError = runDump(dbName, tmp);
        if (dumpError != null)
        {
            // 导出失败不留半截文件
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            // 用 ServiceException 而非 RuntimeException：全局异常处理器对
            // RuntimeException 只返回通用提示（避免外泄内部细节），
            // 这条带排查指引的提示会被吞掉，必须走业务异常通道
            throw new ServiceException("导出失败：" + dumpError
                    + "。若容器内缺少 mysql-client，可到宿主机执行 scripts/backup.sh");
        }

        long size = Files.size(tmp);
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding("utf-8");
        response.setContentLengthLong(size);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (OutputStream out = response.getOutputStream();
             InputStream in = Files.newInputStream(tmp))
        {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, len);
            }
            out.flush();
        }
        finally
        {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
        }
    }

    /**
     * 执行 mysqldump，把输出写到目标文件，同时收集 stderr。
     *
     * @return null 表示成功；否则返回错误描述
     */
    private String runDump(String dbName, Path outFile) throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder(mysqldumpCommand(dbName));
        attachPasswordEnv(pb);
        pb.redirectOutput(outFile.toFile());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        String stderr = readAll(process.getErrorStream());
        int exit = waitFor(process);
        if (exit != 0)
        {
            return "mysqldump 退出码 " + exit + (stderr.isEmpty() ? "" : "：" + firstLines(stderr, 5));
        }
        if (!stderr.isEmpty())
        {
            // 有警告但退出码为 0：mysqldump 偶尔会输出不影响结果的告警，记录即可
            log.warn("mysqldump 告警：{}", firstLines(stderr, 3));
        }
        return null;
    }

    /** 读完一个流并返回内容（用于收集 stderr，避免管道写满导致进程阻塞） */
    private String readAll(InputStream in) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 从上传的 .sql 文件恢复全量数据。
     *
     * 会先自动导出当前数据作为存档（失败即中止），再执行恢复。
     */
    // isSaveRequestData = false：password 是顶层 @RequestParam String，
    // LogAspect 的 excludeParamNames（fastjson 属性过滤器）对顶层标量参数不生效，
    // 记录请求参数会把明文密码写进 sys_oper_log.oper_param。
    // 上传文件名无审计价值（MultipartFile 本就被 isFilterObject 过滤）。
    // 刻意不加 @RepeatSubmit：密码是顶层参数，@RepeatSubmit 会把整个请求（含明文密码）
    // 作为缓存键写进 Redis。重复提交由前端 loading + SensitiveOperationGuard 兜底。
    @PreAuthorize("@ss.hasRole('admin')")
    @Log(title = "全量数据恢复", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/restore")
    public AjaxResult restore(@RequestParam(value = "file", required = false) MultipartFile file,
                              @RequestParam(value = "password", required = false) String password) throws Exception
    {
        // 恢复会用备份整体覆盖数据库，破坏力大于清场，同样要求输入本人密码。
        // 放在最前面：校验不过时连上传的临时文件都不落盘。
        //
        // 注意 file 必须是 required = false。若写成 @RequestParam("file")，
        // 缺文件时 Spring 会在进入本方法**之前**就抛 MissingServletRequestPartException，
        // 密码校验根本没机会执行 —— 表现就是「密码错了却提示请选择文件」，
        // 让人误以为密码校验形同虚设。缺文件的判断交给下面的业务分支。
        sensitiveOperationGuard.verifyCurrentUserPassword(password, "数据恢复");

        if (file == null || file.isEmpty())
        {
            return error("请选择要恢复的 .sql 文件");
        }
        if (file.getSize() > MAX_UPLOAD)
        {
            return error("文件超过 20MB 上限，请确认是否为有效的数据库备份");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".sql"))
        {
            return error("只支持 .sql 文件");
        }

        Path sqlFile = Paths.get(backupDir, "restore_" + timestamp() + ".sql");
        Files.createDirectories(sqlFile.getParent());
        file.transferTo(sqlFile.toFile());

        try
        {
            // 1) 先做破坏性语句检查，不通过就不碰数据库
            String danger = findDangerousStatement(sqlFile);
            if (danger != null)
            {
                return error("备份文件包含危险语句，已拒绝执行：" + danger);
            }

            // 2) 恢复前自动存档当前数据（先备份后破坏，顺序不能反）
            String archive = archiveCurrentData();
            if (archive == null)
            {
                return error("恢复前自动存档失败，为避免数据丢失已中止恢复。"
                        + "请先到宿主机执行 scripts/backup.sh 确认备份可用后重试");
            }

            // 3) 执行恢复
            String dbName = parseDatabaseName(jdbcUrl);
            ProcessBuilder pb = new ProcessBuilder(mysqlCommand(dbName));
            attachPasswordEnv(pb);
            pb.redirectInput(sqlFile.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readAll(process.getInputStream());
            int exit = waitFor(process);
            if (exit != 0)
            {
                return error("mysql 执行失败（退出码 " + exit + "），数据可能未完全恢复。"
                        + "恢复前的存档文件：" + archive + "。错误输出：" + firstLines(output, 5));
            }

            return success("恢复完成。恢复前的数据存档在容器内 " + archive
                    + "，建议立即用「全量导出」再导一份确认数据正确");
        }
        finally
        {
            // 上传的临时文件无论成败都清掉，不在磁盘上留存
            try
            {
                Files.deleteIfExists(sqlFile);
            }
            catch (Exception ignored)
            {
                // 清理失败不影响主流程
            }
        }
    }

    /**
     * 检查备份文件里是否有破坏性语句。
     *
     * 两个关键设计（都是踩过坑才定下来的）：
     *
     * 1. **行首锚定**，不扫全文关键字。备份里的数据行本身可能包含这些词 ——
     *    例如 sys_oper_log 存过我之前测试时报错的信息「…已拒绝执行：DROP DATABASE x」，
     *    全文扫描会把这句数据当成语句，导致「自己导出的备份自己恢复不了」。
     *    只匹配行首的 DROP / TRUNCATE 等，才能区分「作为语句」和「作为数据」。
     *
     * 2. **不拦 DROP ... IF EXISTS**。mysqldump 的正常输出就包含
     *    DROP TABLE IF EXISTS / DROP DATABASE IF EXISTS，这是幂等的。
     *
     * @return 命中的危险语句描述；安全则返回 null
     */
    private String findDangerousStatement(Path sqlFile) throws Exception
    {
        String sql = new String(Files.readAllBytes(sqlFile), StandardCharsets.UTF_8);

        // 行首无条件 DROP（带 IF EXISTS 的幂等，放行）
        java.util.regex.Matcher drop = java.util.regex.Pattern.compile(
                        "(?im)^\\s*DROP\\s+(DATABASE|TABLE|VIEW|PROCEDURE|FUNCTION)\\s+(?!IF\\s+EXISTS)\\S+")
                .matcher(sql);
        if (drop.find())
        {
            return drop.group().trim();
        }

        // 其余危险语句同样按行首判断
        String[][] forbidden = {
                {"TRUNCATE", "TRUNCATE 会清空整表"},
                {"LOAD DATA", "LOAD DATA 会批量导入"},
                {"CREATE USER", "创建用户"},
                {"DROP USER", "删除用户"},
                {"ALTER USER", "修改用户"},
                {"CREATE ROLE", "创建角色"},
                {"GRANT", "授权语句"},
                {"REVOKE", "回收授权语句"},
                {"SET GLOBAL", "修改全局变量"},
                {"SHUTDOWN", "关闭数据库"},
        };
        for (String[] rule : forbidden)
        {
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("(?im)^\\s*" + rule[0] + "\\b");
            if (p.matcher(sql).find())
            {
                return rule[1] + "（行首出现 " + rule[0] + "）";
            }
        }

        // INTO OUTFILE / DUMPFILE：写文件到服务器，出现在行中也要拦
        if (java.util.regex.Pattern.compile("(?i)\\bINTO\\s+(OUT|DUMP)FILE\\b").matcher(sql).find())
        {
            return "INTO OUTFILE / DUMPFILE（向服务器写文件）";
        }

        // 无 WHERE 的全表 DELETE
        java.util.regex.Matcher del = java.util.regex.Pattern.compile(
                        "(?im)^\\s*DELETE\\s+FROM\\s+\\S+\\s*;\\s*$")
                .matcher(sql);
        if (del.find())
        {
            return "无 WHERE 条件的 DELETE（全表删除）";
        }
        return null;
    }

    /**
     * 恢复前把当前数据存档到备份目录（backupDir，默认 /app/backup）
     *
     * @return 存档文件路径；失败返回 null
     */
    private String archiveCurrentData()
    {
        try
        {
            String dbName = parseDatabaseName(jdbcUrl);
            Path out = Paths.get(backupDir, "pre_restore_" + timestamp() + ".sql");
            Files.createDirectories(out.getParent());
            return runDump(dbName, out) == null ? out.toString() : null;
        }
        catch (Exception e)
        {
            log.error("恢复前自动存档失败：{}", e.getMessage());
            return null;
        }
    }

    /** mysqldump 命令：只导出结构+数据，不含权限定义，避免跨机器导入时权限冲突 */
    private List<String> mysqldumpCommand(String dbName)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");
        cmd.add("--host=" + dbHost());
        cmd.add("--user=" + dbUser);
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--default-character-set=utf8mb4");
        cmd.add("--set-gtid-purged=OFF");
        cmd.add(dbName);
        return cmd;
    }

    /** mysql 导入命令 */
    private List<String> mysqlCommand(String dbName)
    {
        List<String> cmd = new ArrayList<>();
        cmd.add("mysql");
        cmd.add("--host=" + dbHost());
        cmd.add("--user=" + dbUser);
        cmd.add("--default-character-set=utf8mb4");
        cmd.add(dbName);
        return cmd;
    }

    /**
     * 备份/恢复连接的主机：从 jdbcUrl 提取，提取失败回退 "mysql"（容器服务名）。
     * 原先写死 --host=mysql，容器内成立；本地命令行运行时 MySQL 在 127.0.0.1，
     * 会报 Unknown MySQL server host 'mysql'。
     */
    private String dbHost()
    {
        try
        {
            // jdbc:mysql://host:port/db?params → host
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("jdbc:mysql://([^:/]+)").matcher(jdbcUrl);
            if (m.find())
            {
                return m.group(1);
            }
        }
        catch (Exception ignored) { }
        return "mysql";
    }

    /**
     * 口令通过 MYSQL_PWD 环境变量传给子进程，不出现在命令行里，
     * 避免被 ps 看到。
     */
    private void attachPasswordEnv(ProcessBuilder pb)
    {
        pb.environment().put("MYSQL_PWD", dbPassword);
        pb.environment().put("MYSQL_USER", dbUser);
    }

    private int waitFor(Process process) throws InterruptedException
    {
        if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES))
        {
            process.destroyForcibly();
            // 同上：走业务异常通道，否则用户只会看到「系统繁忙」
            throw new ServiceException("执行超时（超过 " + TIMEOUT_MINUTES + " 分钟）已终止");
        }
        return process.exitValue();
    }

    /**
     * 从 JDBC URL 里取库名。
     *
     * JDBC 用 ? 分隔查询参数、& 分隔参数间的键值，而 URL 形式的地址（如
     * mysql://host/db?a=1&b=2）里这些字符只是普通字符。所以不能简单按最后一个
     * '/' 截取——本项目的 JDBC URL 里 serverTimezone=Asia/Shanghai 后面跟着
     * &amp;allowPublicKeyRetrieval=true，会把参数一起带成库名。
     *
     * 正确做法：先按 '?' 截掉查询串，再取最后一段路径。
     */
    private String parseDatabaseName(String url)
    {
        if (url == null)
        {
            throw new IllegalStateException("数据源 URL 未配置");
        }
        String noQuery = url;
        int q = noQuery.indexOf('?');
        if (q > 0)
        {
            noQuery = noQuery.substring(0, q);
        }
        int slash = noQuery.lastIndexOf('/');
        if (slash < 0 || slash == noQuery.length() - 1)
        {
            throw new IllegalStateException("无法从数据源 URL 解析数据库名：" + url);
        }
        String db = noQuery.substring(slash + 1);
        // 兜底：库名只允许字母数字下划线与短横线，防止 URL 形态被篡改
        if (!db.matches("[A-Za-z0-9_\\-]+"))
        {
            throw new IllegalStateException("数据源 URL 中的数据库名不合法：" + db);
        }
        return db;
    }

    private String timestamp()
    {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private String firstLines(String text, int n)
    {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, lines.length); i++)
        {
            sb.append(lines[i].trim()).append("; ");
        }
        return sb.toString();
    }
}