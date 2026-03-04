package com.varista.utils;

import org.springframework.stereotype.Component;

@Component
public class ServiceUtils {

    // Helper to handle the "First word = First Name, Rest = Last Name" logic
    public static String[] splitName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return new String[]{"", ""};
        String[] nameArr = fullName.trim().split(" ", 2);
        String first = nameArr[0];
        String last = (nameArr.length > 1) ? nameArr[1] : "";
        return new String[]{first, last};
    }
}
