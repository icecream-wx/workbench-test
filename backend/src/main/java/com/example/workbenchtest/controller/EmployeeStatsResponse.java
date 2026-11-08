package com.example.workbenchtest.controller;

import com.example.workbenchtest.service.EmployeeStats;

import java.util.List;

public record EmployeeStatsResponse(long total, List<DepartmentCountResponse> departments) {

    public record DepartmentCountResponse(String department, long count) {
    }

    public static EmployeeStatsResponse from(EmployeeStats stats) {
        List<DepartmentCountResponse> departments = stats.departments().stream()
                .map(item -> new DepartmentCountResponse(item.department(), item.count()))
                .toList();
        return new EmployeeStatsResponse(stats.total(), departments);
    }
}
