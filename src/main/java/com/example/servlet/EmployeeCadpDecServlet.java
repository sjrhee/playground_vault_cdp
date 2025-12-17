package com.example.servlet;

import com.example.CadpClient;
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

@WebServlet("/api/employee/cadp-dec")
public class EmployeeCadpDecServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        List<Employee> employeeList = new ArrayList<>();
        String url = "jdbc:mysql://mysql:3306/mysql_employees?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
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
        int empNoParam = -1;
        String empNoStr = req.getParameter("empNo");
        if (empNoStr != null && !empNoStr.isEmpty()) {
            try {
                empNoParam = Integer.parseInt(empNoStr);
            } catch (NumberFormatException e) {}
        }

        String sql;
        if (empNoParam != -1) {
            sql = "SELECT emp_no, date_of_birth, first_name, last_name, gender, date_of_hiring, ssn_no FROM employee WHERE emp_no = ?";
        } else {
            sql = "SELECT emp_no, date_of_birth, first_name, last_name, gender, date_of_hiring, ssn_no FROM employee LIMIT ? OFFSET ?";
        }

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (empNoParam != -1) {
                stmt.setInt(1, empNoParam);
            } else {
                stmt.setInt(1, size);
                stmt.setInt(2, offset);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int empNo = rs.getInt("emp_no");
                    LocalDate birthDate = rs.getDate("date_of_birth").toLocalDate();
                    String firstName = rs.getString("first_name");
                    String lastName = rs.getString("last_name");
                    String gender = rs.getString("gender");
                    LocalDate hireDate = rs.getDate("date_of_hiring").toLocalDate();

                    // Decrypt SSN using CADP
                    String ssnRaw = rs.getString("ssn_no");
                    String ssn;
                    try {
                        ssn = CadpClient.getInstance().dec(ssnRaw);
                    } catch (Exception e) {
                        ssn = "Decryption Failed: " + ssnRaw;
                        e.printStackTrace();
                    }

                    employeeList.add(new Employee(empNo, birthDate, firstName, lastName, gender, hireDate, ssn));
                }
            }

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                    .create();

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
