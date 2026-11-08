package com.example.workbenchtest.controller;

import com.example.workbenchtest.domain.Employee;
import com.example.workbenchtest.domain.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String employeeNo,
        String name,
        String gender,
        String phone,
        String email,
        String department,
        String position,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate hireDate,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                (employee.getGender() == null ? Gender.UNKNOWN : employee.getGender()).name(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getHireDate(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
