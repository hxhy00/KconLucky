package com.ruoyi.kcon.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.kcon.service.StaffRoleGuard;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 账号管理集成测试（真实 MySQL + Redis + 真实 HTTP 栈）。
 *
 * 与 MockMvc 单元测试的区别：这里启动完整 Spring 上下文，走 JWT 过滤器、方法级权限、
 * 真实 Mapper/事务与 Redis 会话，验证「接口 + 库 + 缓存」三层联动。
 *
 * 需要外部依赖，默认不参与 `mvn test`；用环境变量显式打开：
 * <pre>
 *   KCON_IT=true \
 *   KCON_IT_DB_URL='jdbc:mysql://127.0.0.1:3306/kcon_lucky?...' \
 *   KCON_IT_DB_USER=root KCON_IT_DB_PASSWORD=root123 \
 *   KCON_IT_REDIS_HOST=127.0.0.1 KCON_IT_REDIS_PORT=6379 \
 *   mvn -pl ruoyi-admin -am test -Dtest=StaffAccountIntegrationTest
 * </pre>
 *
 * @author kcon
 */
@EnabledIfEnvironmentVariable(named = "KCON_IT", matches = "true")
@SpringBootTest(classes = RuoYiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaffAccountIntegrationTest
{
    private static final String ADMIN_PHONE = "13000000000";

    private static final String PASSWORD = "ItPass2026";

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    /** JDK 自带 HttpClient：Spring Boot 4 已移除 TestRestTemplate，集成测试不额外引依赖 */
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StaffRoleGuard staffRoleGuard;

    /** 用例中创建的账号，用例结束后直接从库里清掉 */
    private final List<Long> createdUserIds = new ArrayList<>();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.druid.master.url", () -> env("KCON_IT_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/kcon_lucky?useUnicode=true&characterEncoding=utf8"
                        + "&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true"));
        registry.add("spring.datasource.druid.master.username", () -> env("KCON_IT_DB_USER", "root"));
        registry.add("spring.datasource.druid.master.password", () -> env("KCON_IT_DB_PASSWORD", "root123"));
        registry.add("spring.data.redis.host", () -> env("KCON_IT_REDIS_HOST", "127.0.0.1"));
        registry.add("spring.data.redis.port", () -> env("KCON_IT_REDIS_PORT", "6379"));
        registry.add("token.secret", () -> env("KCON_IT_TOKEN_SECRET",
                "kcon-it-token-secret-0123456789abcdef0123456789abcdef"));
        registry.add("ruoyi.profile", () -> env("KCON_IT_PROFILE_DIR", "/tmp/kcon-it/upload"));
        registry.add("kcon.backup.dir", () -> env("KCON_IT_BACKUP_DIR", "/tmp/kcon-it/backup"));
    }

    @AfterEach
    void cleanUp()
    {
        try
        {
            if (!createdUserIds.isEmpty())
            {
                userService.deleteUserByIds(createdUserIds.toArray(new Long[0]));
                createdUserIds.clear();
            }
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 测试线程里直接调用 Service（断言/清库）时，RuoYi 的 DataScopeAspect 也要求登录上下文；
     * 这里绑一个超管身份，只影响测试线程，不影响 HTTP 请求线程里的真实鉴权。
     */
    @BeforeEach
    void bindAdminContext()
    {
        SysUser admin = userService.selectUserByPhone(ADMIN_PHONE);
        assertThat(admin).as("集成测试库缺少内置超管 %s", ADMIN_PHONE).isNotNull();
        LoginUser loginUser = new LoginUser(admin.getUserId(), admin.getDeptId(), admin,
                permissionService.getMenuPermission(admin));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                loginUser, null, loginUser.getAuthorities()));
    }

    @Test
    @DisplayName("身份下拉：管理员排第一，其后是三个现场岗位")
    void listSelectableIdentities() throws Exception
    {
        JsonNode body = exchange("GET", "/admin/staff/roles", null, adminToken());

        assertThat(body.path("code").asInt()).isEqualTo(200);
        JsonNode data = body.path("data");
        assertThat(data).hasSize(4);
        assertThat(data.get(0).path("roleKey").asText()).isEqualTo(StaffRoleGuard.ROLE_ADMIN);
        // 角色名允许按现场习惯改（基线是「超级管理员」，界面上也可能叫「管理员」）
        assertThat(data.get(0).path("roleName").asText()).isNotBlank();
        assertThat(data.get(3).path("roleKey").asText()).isEqualTo(StaffRoleGuard.ROLE_VERIFY);
    }

    @Test
    @DisplayName("管理员账号 CRUD：建号→列表→编辑→重置密码→删除，全程走真实库与会话")
    void adminAccountFullCrud() throws Exception
    {
        String token = adminToken();
        String phone = uniquePhone();

        // C：创建管理员身份账号
        JsonNode created = exchange("POST", "/admin/staff",
                "{\"nickName\":\"集成管理员\",\"phonenumber\":\"" + phone + "\","
                        + "\"password\":\"" + PASSWORD + "\",\"roleKeys\":[\"admin\"]}", token);
        assertThat(created.path("code").asInt()).isEqualTo(200);

        SysUser saved = userService.selectUserByPhone(phone);
        assertThat(saved).as("账号应已落库").isNotNull();
        createdUserIds.add(saved.getUserId());
        assertThat(staffRoleGuard.hasAdminRole(saved.getUserId())).isTrue();

        // R：列表按手机号检索，身份名与身份 key 都要回填
        JsonNode list = exchange("GET", "/admin/staff/list?phonenumber=" + phone, null, token);
        assertThat(list.path("total").asInt()).isEqualTo(1);
        JsonNode row = list.path("rows").get(0);
        assertThat(row.path("nickName").asText()).isEqualTo("集成管理员");
        assertThat(row.path("roleNames").asText()).isNotBlank();
        assertThat(row.path("roleKeys").get(0).asText()).isEqualTo("admin");

        // U：改姓名 + 追加兑奖台身份
        JsonNode updated = exchange("PUT", "/admin/staff",
                "{\"userId\":" + saved.getUserId() + ",\"nickName\":\"集成管理员（改）\","
                        + "\"phonenumber\":\"" + phone + "\","
                        + "\"roleKeys\":[\"admin\",\"kcon_verify_staff\"]}", token);
        assertThat(updated.path("code").asInt()).isEqualTo(200);

        SysUser afterEdit = userService.selectUserById(saved.getUserId());
        assertThat(afterEdit.getNickName()).isEqualTo("集成管理员（改）");
        assertThat(roleService.selectRoleListByUserId(saved.getUserId())).hasSize(2);

        // 密码重置：库里哈希应能被新口令校验通过
        JsonNode reset = exchange("PUT", "/admin/staff/resetPwd",
                "{\"userId\":" + saved.getUserId() + ",\"password\":\"NewPass2026\"}", token);
        assertThat(reset.path("code").asInt()).isEqualTo(200);
        SysUser afterReset = userService.selectUserById(saved.getUserId());
        assertThat(SecurityUtils.matchesPassword("NewPass2026", afterReset.getPassword())).isTrue();

        // D：删除后按手机号查不到，已签发令牌一并失效
        JsonNode deleted = exchange("DELETE", "/admin/staff/" + saved.getUserId(), null, token);
        assertThat(deleted.path("code").asInt()).isEqualTo(200);
        assertThat(userService.selectUserByPhone(phone)).isNull();
        createdUserIds.clear();
    }

    @Test
    @DisplayName("权限边界：岗位账号不能管理账号、不能抽奖；匿名请求被拦截")
    void staffCannotCrossIdentityBoundaries() throws Exception
    {
        String adminToken = adminToken();
        String phone = uniquePhone();

        JsonNode created = exchange("POST", "/admin/staff",
                "{\"nickName\":\"集成签到员\",\"phonenumber\":\"" + phone + "\","
                        + "\"password\":\"" + PASSWORD + "\",\"roleKeys\":[\"kcon_checkin_staff\"]}", adminToken);
        assertThat(created.path("code").asInt()).isEqualTo(200);

        SysUser staff = userService.selectUserByPhone(phone);
        createdUserIds.add(staff.getUserId());
        String staffToken = tokenOf(staff);

        // 岗位账号没有 kcon:staff:* / kcon:draw:operate：方法级权限拒绝（HTTP 200 + body code 403）
        JsonNode staffList = exchange("GET", "/admin/staff/list", null, staffToken);
        assertThat(staffList.path("code").asInt()).isEqualTo(403);

        JsonNode draw = exchange("POST", "/admin/draw/draw", "{\"sessionCode\":\"AM\"}", staffToken);
        assertThat(draw.path("code").asInt()).isEqualTo(403);

        // 岗位账号可以读自己有权用的名单接口（保证「拒绝」不是一刀切）
        JsonNode attendeeList = exchange("GET", "/admin/attendee/list", null, staffToken);
        assertThat(attendeeList.path("code").asInt()).isEqualTo(200);

        // 匿名访问管理端必须被拦（RuoYi 的 401 走响应体 code，HTTP 状态仍为 200）
        HttpResponse<String> anonymous = http.send(HttpRequest.newBuilder(URI.create(url("/admin/attendee/list")))
                .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(JSON.readTree(anonymous.body()).path("code").asInt()).isEqualTo(401);
    }

    @Test
    @DisplayName("内置超管保护：不能删除/停用当前登录的超管账号")
    void builtinAdminIsProtected() throws Exception
    {
        String token = adminToken();

        JsonNode remove = exchange("DELETE", "/admin/staff/1", null, token);
        assertThat(remove.path("code").asInt()).isEqualTo(500);
        assertThat(remove.path("msg").asText()).isEqualTo("不能删除当前登录的账号");

        JsonNode disable = exchange("PUT", "/admin/staff/changeStatus",
                "{\"userId\":1,\"status\":\"1\"}", token);
        assertThat(disable.path("code").asInt()).isEqualTo(500);
        assertThat(disable.path("msg").asText()).isEqualTo("不能停用当前登录的账号");
    }

    private String adminToken()
    {
        SysUser admin = userService.selectUserByPhone(ADMIN_PHONE);
        assertThat(admin).as("集成测试库缺少内置超管 %s，请先执行 sql/00-ry-base.sql", ADMIN_PHONE).isNotNull();
        return tokenOf(admin);
    }

    private String tokenOf(SysUser user)
    {
        LoginUser loginUser = new LoginUser(user.getUserId(), user.getDeptId(), user,
                permissionService.getMenuPermission(user));
        // TokenService 会读取当前请求的 User-Agent 记录登录设备；
        // 测试线程没有请求上下文，这里补一个（真实链路里由 TokenFilter/SecurityFilter 提供）
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("User-Agent", "kcon-integration-test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        try
        {
            return tokenService.createToken(loginUser);
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private JsonNode exchange(String method, String path, String body, String token) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json");
        if (token != null)
        {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.body()).as("响应体不能为空：%s %s", method, path).isNotBlank();
        return JSON.readTree(response.body());
    }

    private String url(String path)
    {
        return "http://127.0.0.1:" + port + path;
    }

    /** 11 位手机号：139 + 纳秒后 8 位，保证同一进程内多次调用不重复 */
    private String uniquePhone()
    {
        return "139" + String.format("%08d", Math.abs(System.nanoTime()) % 100_000_000L);
    }

    private static String env(String key, String defaultValue)
    {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
