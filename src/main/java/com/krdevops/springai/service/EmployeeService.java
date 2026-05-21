package com.krdevops.springai.service;

import com.krdevops.springai.mapper.EmployeeRepository;
import com.krdevops.springai.vo.EmployeeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public List<EmployeeVO> getEmployeeList(String keyword) {
        return employeeRepository.selectEmployeeList(keyword);
    }

    public EmployeeVO getEmployee(String emplyrId) {
        return employeeRepository.selectEmployee(emplyrId);
    }

    public int createEmployee(EmployeeVO vo) {
        return employeeRepository.insertEmployee(vo);
    }

    public int updateEmployee(EmployeeVO vo) {
        return employeeRepository.updateEmployee(vo);
    }

    public int deleteEmployee(String emplyrId) {
        return employeeRepository.deleteEmployee(emplyrId);
    }
}
