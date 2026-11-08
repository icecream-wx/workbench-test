package com.example.workbenchtest.service;

import java.util.List;

public record EmployeeStats(long total, List<DepartmentCount> departments) {

    public record DepartmentCount(String department, long count) {
    }
}
