package com.krdevops.springai.service;

import com.krdevops.springai.mapper.EmployeeRepository;
import com.krdevops.springai.vo.EmployeeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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
        // 비밀번호 미전달 시 무작위 임시 비밀번호 생성 — 하드코딩 방지
        String raw = (vo.getPassword() != null && !vo.getPassword().isBlank())
            ? vo.getPassword()
            : UUID.randomUUID().toString();
        vo.setPassword(new BCryptPasswordEncoder().encode(raw));
        return employeeRepository.insertEmployee(vo);
    }

    public int updateEmployee(EmployeeVO vo) {
        return employeeRepository.updateEmployee(vo);
    }

    public int deleteEmployee(String emplyrId) {
        return employeeRepository.deleteEmployee(emplyrId);
    }
}
