package com.example.servlet;

import com.example.CrdpClient;
import com.example.model.Employee;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/employee/crdp-dec")
public class EmployeeCrdpDecServlet extends HttpServlet {

    // CRDP Configuration
    // Loaded from src/main/resources/crdp.properties
    private String crdpEndpoint;
    private String crdpPolicy;
    private String crdpUser;
    private String crdpJwt;

    private CrdpClient crdp;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            java.util.Properties props = new java.util.Properties();
            try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("crdp.properties")) {
                if (is == null) {
                    throw new ServletException("Configuration file 'crdp.properties' not found in classpath");
                }
                props.load(is);
            }

            this.crdpEndpoint = props.getProperty("crdp_endpoint");
            this.crdpPolicy = props.getProperty("crdp_policy");
            this.crdpUser = props.getProperty("crdp_user_name");
            this.crdpJwt = props.getProperty("crdp_jwt");

            if (this.crdpEndpoint == null || this.crdpPolicy == null || this.crdpJwt == null) {
                throw new ServletException("Missing required CRDP configuration in crdp.properties");
            }

            this.crdp = new CrdpClient(crdpEndpoint, crdpPolicy, crdpJwt, crdpUser);
            this.crdp.warmup();

            this.gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                    .create();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize CrdpClient: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        List<Employee> employeeList = new ArrayList<>();
        // Use updated database name
        String url = "jdbc:mysql://mysql:3306/mysql_employees?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String dbUser = "testuser";
        String dbPassword = "testpassword";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, "JDBC Driver not found");
            return;
        }

        int page = 0;
        int size = 100;
        try {
            String pageParam = req.getParameter("page");
            if (pageParam != null)
                page = Integer.parseInt(pageParam);
            String sizeParam = req.getParameter("size");
            if (sizeParam != null)
                size = Integer.parseInt(sizeParam);
        } catch (NumberFormatException e) {
        }

        int offset = page * size;
        String sql = "SELECT emp_no, date_of_birth, first_name, last_name, gender, date_of_hiring, ssn_no FROM employee LIMIT ? OFFSET ?";

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, size);
            stmt.setInt(2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int empNo = rs.getInt("emp_no");
                    LocalDate birthDate = rs.getDate("date_of_birth").toLocalDate();
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    String gender = rs.getString("gender");
                    LocalDate hireDate = rs.getDate("date_of_hiring").toLocalDate();

                    // 복호화 수행 (CRDP reveal 호출)
                    String encrypted = rs.getString("ssn_no");
                    String ssn;
                    try {
                        // 복호화 수행 (reveal -> dec 변경)
                        ssn = crdp.dec(encrypted);
                    } catch (Exception e) {
                        ssn = "Decryption Failed: " + encrypted;
                        // 로그를 남기는 것이 좋지만 여기선 스택트레이스 출력만
                        e.printStackTrace();
                    }

                    employeeList.add(new Employee(empNo, birthDate, firstName, lastName, gender, hireDate, ssn));
                }
            }

            // Gson 인스턴스 재사용 (init에서 초기화됨)

            PrintWriter out = resp.getWriter();
            out.print(gson.toJson(employeeList));
            out.flush();

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, "Database error: " + e.getMessage());
        }
    }

    private void writeError(HttpServletResponse resp, String message) throws IOException {
        PrintWriter out = resp.getWriter();
        out.print("{\"error\": \"" + message + "\"}");
        out.flush();
    }

    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter jsonWriter, LocalDate localDate) throws IOException {
            if (localDate == null)
                jsonWriter.nullValue();
            else
                jsonWriter.value(localDate.toString());
        }

        @Override
        public LocalDate read(JsonReader jsonReader) throws IOException {
            if (jsonReader.peek() == com.google.gson.stream.JsonToken.NULL) {
                jsonReader.nextNull();
                return null;
            } else {
                return LocalDate.parse(jsonReader.nextString());
            }
        }
    }
}
