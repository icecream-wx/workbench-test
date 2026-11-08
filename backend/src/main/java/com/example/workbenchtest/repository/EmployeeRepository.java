package com.example.workbenchtest.repository;

import com.example.workbenchtest.domain.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeNoIgnoreCase(String employeeNo);

    boolean existsByEmployeeNoIgnoreCaseAndIdNot(String employeeNo, Long id);

    @Query("""
            select e from Employee e
            where lower(e.employeeNo) like lower(concat('%', :keyword, '%'))
               or lower(e.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<Employee> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select e.department as department, count(e) as total
            from Employee e
            group by e.department
            """)
    List<DepartmentCount> countByDepartment();

    interface DepartmentCount {

        String getDepartment();

        long getTotal();
    }
}
