package com.lms.tenancy.internal;

import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo data for the interview (local only): {@code ./gradlew bootRun --args='--lms.seed=true --server.port=0'}.
 *
 * <ul>
 *   <li>데모전자 (demo-elec): admin, instructor, learner + 17 employees in 4 departments, 3 published
 *       courses, assignments with deadlines, and enrollments at every stage (done with certificate,
 *       in progress, not started, overdue).</li>
 *   <li>데모물산 (demo-trade): a second, small company — sign in to both to show they never see each other.</li>
 * </ul>
 * Keycloak accounts get a known dev password (README). Data goes in with plain SQL inside each
 * company's tenant context, so RLS applies to the seeder too. Re-running skips existing companies.
 */
@Component
@ConditionalOnProperty(name = "lms.seed", havingValue = "true")
class DemoSeeder implements ApplicationRunner {

    static final String PASSWORD = "Demo-2026-lms";   // realm policy: 10+ characters
    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final KeycloakAdminClient keycloak;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ApplicationContext context;

    DemoSeeder(KeycloakAdminClient keycloak, JdbcClient jdbc, PlatformTransactionManager txManager, ApplicationContext context) {
        this.keycloak = keycloak;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.context = context;
    }

    record Person(String last, String first, String local, String role, String department) {
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("데모전자", "demo-elec", List.of(
                new Person("김", "하나", "admin", "ORG_ADMIN", "인사팀"),
                new Person("이", "도윤", "instructor", "INSTRUCTOR", "인사팀"),
                new Person("박", "서연", "learner", "LEARNER", "개발팀"),
                new Person("최", "민준", "minjun.choi", "LEARNER", "개발팀"),
                new Person("정", "지우", "jiwoo.jung", "LEARNER", "개발팀"),
                new Person("강", "하준", "hajun.kang", "LEARNER", "개발팀"),
                new Person("조", "서윤", "seoyun.jo", "LEARNER", "개발팀"),
                new Person("윤", "도현", "dohyun.yoon", "LEARNER", "개발팀"),
                new Person("장", "예은", "yeeun.jang", "LEARNER", "영업팀"),
                new Person("임", "시우", "siwoo.lim", "LEARNER", "영업팀"),
                new Person("한", "수아", "sua.han", "LEARNER", "영업팀"),
                new Person("오", "주원", "juwon.oh", "LEARNER", "영업팀"),
                new Person("서", "지민", "jimin.seo", "LEARNER", "영업팀"),
                new Person("신", "건우", "gunwoo.shin", "LEARNER", "인사팀"),
                new Person("권", "채원", "chaewon.kwon", "LEARNER", "인사팀"),
                new Person("황", "우진", "woojin.hwang", "LEARNER", "재무팀"),
                new Person("안", "다은", "daeun.ahn", "LEARNER", "재무팀"),
                new Person("송", "준서", "junseo.song", "LEARNER", "재무팀"),
                new Person("류", "하은", "haeun.ryu", "LEARNER", "재무팀"),
                new Person("홍", "지호", "jiho.hong", "LEARNER", "개발팀")), true);
        seed("데모물산", "demo-trade", List.of(
                new Person("백", "승민", "admin", "ORG_ADMIN", "경영지원팀"),
                new Person("문", "유나", "yuna.moon", "LEARNER", "경영지원팀"),
                new Person("배", "현우", "hyunwoo.bae", "LEARNER", "물류팀"),
                new Person("노", "소율", "soyul.noh", "LEARNER", "물류팀")), false);
        log.info("Demo data ready. Password for every demo account: {}", PASSWORD);
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void seed(String name, String slug, List<Person> people, boolean full) {
        if (jdbc.sql("select count(*) from tenant where slug = ?").param(slug).query(Long.class).single() > 0) {
            log.info("{} already exists, skipping", slug);
            return;
        }
        String domain = slug + ".test";
        // Accounts first (idempotent: re-running after a failure reuses them), the organization and
        // the tenant row last, so a failed run leaves nothing that makes the next run skip.
        Map<Person, String> subs = new LinkedHashMap<>();
        for (Person p : people) {
            subs.put(p, keycloak.provisionUser(p.local() + "@" + domain, p.first(), p.last(), PASSWORD));
        }
        String orgId = keycloak.createOrganization(name, slug, domain);
        UUID tenantId = UuidV7.generate();
        jdbc.sql("insert into tenant (id, name, slug, keycloak_org_id) values (?, ?, ?, ?)")
                .params(tenantId, name, slug, orgId).update();

        Map<Person, UUID> userIds = new LinkedHashMap<>();
        for (Person p : people) {
            String email = p.local() + "@" + domain;
            String sub = subs.get(p);
            keycloak.addMemberIfAbsent(orgId, sub);
            userIds.put(p, jdbc.sql("""
                            insert into app_user (id, keycloak_sub, email, name) values (?, ?, ?, ?)
                            on conflict (keycloak_sub) do update set email = excluded.email, name = excluded.name
                            returning id
                            """)
                    .params(UuidV7.generate(), sub, email, p.first() + " " + p.last())
                    .query(UUID.class).single());
        }

        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> {
            Instant now = Instant.now();
            Map<String, UUID> departments = new LinkedHashMap<>();
            people.stream().map(Person::department).distinct().forEach(d -> {
                UUID id = UuidV7.generate();
                jdbc.sql("insert into department (id, tenant_id, name) values (?, ?, ?)").params(id, tenantId, d).update();
                departments.put(d, id);
            });
            Map<Person, UUID> memberships = new LinkedHashMap<>();
            for (Person p : people) {
                UUID id = UuidV7.generate();
                jdbc.sql("""
                                insert into membership (id, tenant_id, user_id, role, status, department_id, joined_at)
                                values (?, ?, ?, ?, 'ACTIVE', ?, ?)
                                """)
                        .params(id, tenantId, userIds.get(p), p.role(), departments.get(p.department()),
                                ts(now.minus(30, ChronoUnit.DAYS)))
                        .update();
                memberships.put(p, id);
            }
            UUID owner = memberships.entrySet().stream().filter(e -> !e.getKey().role().equals("LEARNER"))
                    .reduce((a, b) -> b).orElseThrow().getValue();
            UUID admin = memberships.get(people.getFirst());

            List<Course> courses = full ? List.of(
                    course(tenantId, owner, "정보보안 기초", "피싱 메일과 비밀번호, 사내 자료를 다루는 기본 원칙을 20분 안에 익힙니다.",
                            List.of("피싱 메일 알아보기", "안전한 비밀번호", "사내 자료 반출 규칙"), true),
                    course(tenantId, owner, "직장 내 괴롭힘 예방", "법이 정한 괴롭힘의 기준과 신고 절차를 알아봅니다.",
                            List.of("괴롭힘의 정의", "신고와 보호 절차"), false),
                    course(tenantId, owner, "개인정보보호 교육", "고객 정보를 다룰 때 지켜야 할 수집·이용·파기 원칙.",
                            List.of("개인정보란 무엇인가", "수집과 파기"), true))
                    : List.of(course(tenantId, owner, "물류 현장 안전", "지게차와 적재 구역 안전 수칙.",
                            List.of("적재 구역 수칙", "지게차 신호"), false));

            // Assignments: who takes what, by when (one overdue on purpose for the dashboard).
            Instant soon = now.plus(4, ChronoUnit.DAYS);
            Instant overdue = now.minus(2, ChronoUnit.DAYS);
            Instant later = now.plus(25, ChronoUnit.DAYS);
            int i = 0;
            for (Course c : courses) {
                Instant due = i == 0 ? soon : i == 1 ? overdue : later;
                List<String> targets = new ArrayList<>(departments.keySet());
                if (full && i == 1) {
                    targets = List.of("영업팀", "인사팀");
                } else if (full && i == 2) {
                    targets = List.of("개발팀", "재무팀");
                }
                for (String d : targets) {
                    jdbc.sql("""
                                    insert into assignment (id, tenant_id, course_id, target_type, department_id, due_at, assigned_by, created_at)
                                    values (?, ?, ?, 'DEPARTMENT', ?, ?, ?, ?)
                                    """)
                            .params(UuidV7.generate(), tenantId, c.id(), departments.get(d), ts(due), admin,
                                    ts(now.minus(10, ChronoUnit.DAYS)))
                            .update();
                }
                int n = 0;
                for (var e : memberships.entrySet()) {
                    if (!targets.contains(e.getKey().department())) {
                        continue;
                    }
                    // Every stage on the dashboard: done / in progress / not started, cycling per person.
                    String stage = switch ((n++ + i) % 4) {
                        case 0, 1 -> "COMPLETED";
                        case 2 -> "IN_PROGRESS";
                        default -> "NOT_STARTED";
                    };
                    enroll(tenantId, e.getValue(), c, due, stage, now);
                }
                i++;
            }
        }));
        log.info("Seeded {} ({} people)", slug, people.size());
    }

    record Course(UUID id, List<UUID> lessons, boolean quiz) {
    }

    private Course course(UUID tenantId, UUID owner, String title, String description, List<String> lessonTitles,
                          boolean withQuiz) {
        UUID courseId = UuidV7.generate();
        Instant published = Instant.now().minus(14, ChronoUnit.DAYS);
        jdbc.sql("""
                        insert into course (id, tenant_id, title, description, status, owner_id, published_at, created_at, updated_at)
                        values (?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)
                        """)
                .params(courseId, tenantId, title, description, owner, ts(published), ts(published), ts(published))
                .update();
        UUID section = UuidV7.generate();
        jdbc.sql("insert into course_section (id, tenant_id, course_id, title, position) values (?, ?, ?, '1장', 0)")
                .params(section, tenantId, courseId).update();
        List<UUID> lessons = new ArrayList<>();
        for (int p = 0; p < lessonTitles.size(); p++) {
            UUID lesson = UuidV7.generate();
            jdbc.sql("""
                            insert into lesson (id, tenant_id, course_id, section_id, title, type, position, body)
                            values (?, ?, ?, ?, ?, 'TEXT', ?, ?)
                            """)
                    .params(lesson, tenantId, courseId, section, lessonTitles.get(p), p,
                            lessonTitles.get(p) + "\n\n이 레슨은 데모용 본문입니다. 업무에서 지켜야 할 원칙을 읽고 '다 읽었어요'를 누르세요.")
                    .update();
            lessons.add(lesson);
        }
        if (withQuiz) {
            UUID quiz = UuidV7.generate();
            jdbc.sql("insert into quiz (id, tenant_id, course_id, pass_score, max_attempts, revision) values (?, ?, ?, 80, 3, 1)")
                    .params(quiz, tenantId, courseId).update();
            jdbc.sql("""
                            insert into question (id, tenant_id, quiz_id, type, prompt, options, answer, points, position)
                            values (?, ?, ?, 'SINGLE', ?, cast(? as jsonb), cast(? as jsonb), 50, 0),
                                   (?, ?, ?, 'MULTIPLE', ?, cast(? as jsonb), cast(? as jsonb), 50, 1)
                            """)
                    .params(UuidV7.generate(), tenantId, quiz, title + ": 가장 먼저 해야 할 일은?",
                            "[{\"id\":\"a\",\"text\":\"그냥 넘어간다\"},{\"id\":\"b\",\"text\":\"담당 부서에 알린다\"},{\"id\":\"c\",\"text\":\"동료에게만 말한다\"}]",
                            "[\"b\"]",
                            UuidV7.generate(), tenantId, quiz, title + ": 맞는 것을 모두 고르세요.",
                            "[{\"id\":\"a\",\"text\":\"규칙을 확인한다\"},{\"id\":\"b\",\"text\":\"기록을 남긴다\"},{\"id\":\"c\",\"text\":\"비밀번호를 공유한다\"}]",
                            "[\"a\",\"b\"]")
                    .update();
        }
        return new Course(courseId, lessons, withQuiz);
    }

    private void enroll(UUID tenantId, UUID membership, Course c, Instant due, String stage, Instant now) {
        UUID enrollment = UuidV7.generate();
        Instant started = stage.equals("NOT_STARTED") ? null : now.minus(5, ChronoUnit.DAYS);
        Instant completed = stage.equals("COMPLETED") ? now.minus(3, ChronoUnit.DAYS) : null;
        jdbc.sql("""
                        insert into enrollment (id, tenant_id, membership_id, course_id, status, due_at, started_at, completed_at, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(enrollment, tenantId, membership, c.id(), stage, ts(due), ts(started), ts(completed),
                        ts(now.minus(10, ChronoUnit.DAYS)))
                .update();
        int done = switch (stage) {
            case "COMPLETED" -> c.lessons().size();
            case "IN_PROGRESS" -> 1;
            default -> 0;
        };
        for (int l = 0; l < done; l++) {
            jdbc.sql("""
                            insert into lesson_progress (id, tenant_id, enrollment_id, lesson_id, completed_at)
                            values (?, ?, ?, ?, ?)
                            """)
                    .params(UuidV7.generate(), tenantId, enrollment, c.lessons().get(l), ts(now.minus(4, ChronoUnit.DAYS)))
                    .update();
        }
        if (completed != null) {
            // The PDF is rendered by the certificate backfill job once the API runs.
            jdbc.sql("insert into certificate (id, tenant_id, enrollment_id, serial, issued_at) values (?, ?, ?, ?, ?)")
                    .params(UuidV7.generate(), tenantId, enrollment, "LMS-" + Year.now() + "-" + code(), ts(completed))
                    .update();
        }
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static String code() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CROCKFORD[RANDOM.nextInt(CROCKFORD.length)]);
        }
        return sb.toString();
    }
}
