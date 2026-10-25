package com.campusplan;

import com.campusplan.application.StudentService;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.ui.CampusPlanCli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        new CampusPlanCli(students, in, System.out).run();
    }
}
