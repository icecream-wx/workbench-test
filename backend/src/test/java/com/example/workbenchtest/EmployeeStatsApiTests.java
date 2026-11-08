package com.example.workbenchtest;

import com.example.workbenchtest.domain.Employee;
import com.example.workbenchtest.domain.Gender;
import com.example.workbenchtest.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeStatsApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    private int sequence;

    @BeforeEach
    void cleanDatabase() {
        employeeRepository.deleteAll();
        sequence = 0;
    }

    @Test
    void t1EmptyDatabaseReturnsZeroTotalAndEmptyDepartments() throws Exception {
        Call call = getStats();

        assertEquals(200, call.status());
        assertEquals(0, call.code());
        assertEquals("ok", call.message());
        assertEquals(0, call.data().path("total").asInt());
        JsonNode departments = call.data().path("departments");
        assertTrue(departments.isArray());
        assertEquals(0, departments.size());
    }

    @Test
    void t2SingleDepartmentIsCounted() throws Exception {
        saveEmployees("研发部", 3);

        Call call = getStats();

        assertEquals(0, call.code());
        assertEquals(3, call.data().path("total").asInt());
        JsonNode departments = call.data().path("departments");
        assertEquals(1, departments.size());
        assertEquals("研发部", departments.get(0).path("department").asText());
        assertEquals(3, departments.get(0).path("count").asInt());
    }

    @Test
    void t3MultipleDepartmentsAreGrouped() throws Exception {
        saveEmployees("研发部", 3);
        saveEmployees("市场部", 2);
        saveEmployees("人事部", 1);

        Call call = getStats();

        JsonNode departments = call.data().path("departments");
        assertEquals(6, call.data().path("total").asInt());
        assertEquals(3, departments.size());
        assertEquals(3, findDepartment(departments, "研发部").path("count").asInt());
        assertEquals(2, findDepartment(departments, "市场部").path("count").asInt());
        assertEquals(1, findDepartment(departments, "人事部").path("count").asInt());
        assertEquals(call.data().path("total").asInt(), sumCounts(departments));
    }

    @Test
    void t4BlankDepartmentsAreMergedIntoUnassigned() throws Exception {
        saveEmployee("E401", null);
        saveEmployee("E402", null);
        saveEmployee("E403", "");
        saveEmployee("E404", "   ");

        Call call = getStats();

        JsonNode departments = call.data().path("departments");
        assertEquals(4, call.data().path("total").asInt());
        assertEquals(1, departments.size());
        JsonNode unassigned = departments.get(0);
        assertEquals("未分配", unassigned.path("department").asText());
        assertEquals(4, unassigned.path("count").asInt());
        for (JsonNode item : departments) {
            String department = item.path("department").asText();
            assertFalse(department.isBlank(), "no blank department group is expected");
        }
    }

    @Test
    void t5DepartmentsAreSortedByCountDescending() throws Exception {
        saveEmployees("研发部", 5);
        saveEmployees("市场部", 2);
        saveEmployees("人事部", 1);

        Call call = getStats();

        JsonNode departments = call.data().path("departments");
        assertEquals(3, departments.size());
        assertEquals(5, departments.get(0).path("count").asInt());
        assertEquals(2, departments.get(1).path("count").asInt());
        assertEquals(1, departments.get(2).path("count").asInt());
        assertEquals("研发部", departments.get(0).path("department").asText());
    }

    @Test
    void t6TiedCountsAreSortedByDepartmentAscendingAndStable() throws Exception {
        saveEmployees("研发部", 2);
        saveEmployees("市场部", 2);
        saveEmployees("人事部", 1);

        Call first = getStats();
        Call second = getStats();

        JsonNode departments = first.data().path("departments");
        assertEquals(3, departments.size());
        // STAT-01 规则为 count 降序 + department 升序；按 Unicode 码点（与拼音一致）
        // “市场部” < “研发部”，因此并列时市场部排在研发部之前（STAT-03 T6 括号内示例与此矛盾，
        // 以 STAT-01 的升序规则为准，见交付证据“未解决项”）。
        assertEquals("市场部", departments.get(0).path("department").asText());
        assertEquals("研发部", departments.get(1).path("department").asText());
        assertEquals("人事部", departments.get(2).path("department").asText());
        assertTrue(departments.get(0).path("department").asText()
                .compareTo(departments.get(1).path("department").asText()) < 0);
        assertEquals(departments, second.data().path("departments"));
    }

    @Test
    void t7UnassignedParticipatesInTieBreakOrdering() throws Exception {
        saveEmployees("研发部", 3);
        saveEmployees("市场部", 1);
        saveEmployees("未分配", 1);

        Call call = getStats();

        JsonNode departments = call.data().path("departments");
        assertEquals(3, departments.size());
        assertEquals("研发部", departments.get(0).path("department").asText());
        assertEquals("市场部", departments.get(1).path("department").asText());
        assertEquals("未分配", departments.get(2).path("department").asText());
    }

    @Test
    void t8StatsPathIsNotCapturedByDetailRoute() throws Exception {
        saveEmployee("E801", "研发部");

        Call call = getStats();

        assertEquals(200, call.status());
        assertEquals(0, call.code());
        assertEquals("ok", call.message());
        assertEquals(1, call.data().path("total").asInt());
        assertTrue(call.data().path("departments").isArray());
    }

    @Test
    void t9StatsIsReadOnly() throws Exception {
        saveEmployees("研发部", 2);
        saveEmployees("市场部", 1);
        long before = employeeRepository.count();

        getStats();

        assertEquals(before, employeeRepository.count());
        assertEquals(3, employeeRepository.count());
    }

    private JsonNode findDepartment(JsonNode departments, String department) {
        for (JsonNode item : departments) {
            if (department.equals(item.path("department").asText())) {
                return item;
            }
        }
        throw new AssertionError("department not found: " + department);
    }

    private int sumCounts(JsonNode departments) {
        int sum = 0;
        for (JsonNode item : departments) {
            sum += item.path("count").asInt();
        }
        return sum;
    }

    private void saveEmployees(String department, int total) {
        for (int index = 1; index <= total; index++) {
            saveEmployee(String.format("E%03d", ++sequence), department);
        }
    }

    private void saveEmployee(String employeeNo, String department) {
        Employee employee = new Employee();
        employee.setEmployeeNo(employeeNo);
        employee.setName("员工" + employeeNo);
        employee.setGender(Gender.UNKNOWN);
        employee.setDepartment(department);
        employee.setHireDate(LocalDate.of(2024, 1, 31));
        employee.setCreatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        employee.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        employeeRepository.save(employee);
    }

    private Call getStats() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/employees/stats")).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = body.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        return new Call(result.getResponse().getStatus(), node);
    }

    private record Call(int status, JsonNode body) {

        int code() {
            return body.path("code").asInt(-1);
        }

        String message() {
            return body.path("message").asText();
        }

        JsonNode data() {
            return body.path("data");
        }
    }
}
