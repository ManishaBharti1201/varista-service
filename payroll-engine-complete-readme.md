
# Payroll Engine Technical Design Specification

## Overview

This document describes a payroll calculation engine reverse engineered from attendance reports and payroll registers. The design is intended for implementation in a Java / Spring Boot payroll service.

---

# Payroll Processing Flow

```text
Clock In / Clock Out
        |
        v
Attendance Processing
        |
        v
Attendance Summary
        |
        v
Overtime Calculation
        |
        v
Salary Component Proration
        |
        v
Gross Salary Calculation
        |
        v
Statutory Deductions
        |
        v
Net Salary Calculation
```

---

# Attendance Status Definitions

| Status | Meaning | Payable |
|----------|----------|----------|
| P | Present | Yes |
| O | Present with overtime generating shift | Yes |
| A | Absent | No |
| AB | Absent | No |
| W/O | Weekly Off | Yes |
| W/P | Worked on Weekly Off | Yes |
| N/H | National Holiday | Yes |
| AP | Partial Attendance | Company-specific |
| E | Exception Attendance Status | Company-specific |

---

# Payroll Constants

```java
public final class PayrollConstants {

    public static final int MONTH_DIVISOR = 26;
    public static final int SHIFT_HOURS = 8;

    public static final BigDecimal PF_RATE = new BigDecimal("0.12");
    public static final BigDecimal ESIC_RATE = new BigDecimal("0.0075");

    public static final BigDecimal PF_WAGE_CEILING = new BigDecimal("15000");
}
```

---

# Domain Model

## Employee

```java
public class Employee {

    private String employeeId;
    private String employeeName;

    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal interimBonus;
    private BigDecimal washingAllowance;
    private BigDecimal specialAllowance;
}
```

## AttendanceSummary

```java
public class AttendanceSummary {

    private int workedDays;
    private int overtimeHours;
    private int weeklyOffWorkedDays;
    private int nationalHolidayDays;

}
```

## PayrollResult

```java
public class PayrollResult {

    private BigDecimal grossSalary;
    private BigDecimal pf;
    private BigDecimal esic;
    private BigDecimal professionalTax;
    private BigDecimal netSalary;
}
```

---

# Worked Days Calculation

## Payable Statuses

```text
P
O
W/O
W/P
N/H
```

## Non-Payable Statuses

```text
A
AB
```

## Formula

```java
workedDays =
      presentDays
    + eligibleWeeklyOffs
    + nationalHolidayDays;
```

---

# Salary Component Proration

All salary components are prorated using 26 days.

## Formula

```text
Earned Component
=
Monthly Component × Worked Days ÷ 26
```

## Earned Basic

```text
Earned Basic
=
Basic Salary × Worked Days ÷ 26
```

## Earned HRA

```text
Earned HRA
=
HRA × Worked Days ÷ 26
```

## Earned Interim Bonus

```text
Earned Interim Bonus
=
Interim Bonus × Worked Days ÷ 26
```

## Earned Washing Allowance

```text
Earned Washing Allowance
=
Washing Allowance × Worked Days ÷ 26
```

## Earned Special Allowance

```text
Earned Special Allowance
=
Special Allowance × Worked Days ÷ 26
```

## Utility Method

```java
private BigDecimal prorate(BigDecimal amount,
                           int workedDays) {

    return amount
            .multiply(BigDecimal.valueOf(workedDays))
            .divide(BigDecimal.valueOf(26),
                    2,
                    RoundingMode.HALF_UP);
}
```

---

# Overtime Calculation

## Business Rule

Overtime is calculated using Basic Salary only.

It is not calculated using Gross Salary.

## Hourly Rate

```text
Hourly Rate
=
Basic Salary ÷ 26 ÷ 8
```

## Overtime Rate

```text
OT Rate
=
Hourly Rate × 2
```

## Overtime Amount

```text
OT Amount
=
(Basic Salary ÷ 26 ÷ 8)
× 2
× OT Hours
```

## Java Implementation

```java
public BigDecimal calculateOtAmount(
        BigDecimal basicSalary,
        int otHours) {

    BigDecimal hourlyRate =
            basicSalary
                .divide(BigDecimal.valueOf(26),
                        8,
                        RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(8),
                        8,
                        RoundingMode.HALF_UP);

    return hourlyRate
            .multiply(BigDecimal.valueOf(2))
            .multiply(BigDecimal.valueOf(otHours))
            .setScale(2, RoundingMode.HALF_UP);
}
```

---

# Weekly Off Overtime

## Formula

```text
Rest OT Hours
=
Weekly Off Worked Days × 8
```

Example:

```text
2 Weekly Off Days Worked
=
16 Rest OT Hours
```

## Total OT Hours

```java
totalOtHours =
        normalOtHours
      + (weeklyOffWorkedDays * 8);
```

---

# Gross Salary Calculation

## Formula

```text
Gross Salary
=
Earned Basic
+ Earned HRA
+ Earned Interim Bonus
+ Earned Washing Allowance
+ Earned Special Allowance
+ OT Amount
+ NH Amount
+ Other Earnings
```

## Java

```java
BigDecimal grossSalary =
        earnedBasic
            .add(earnedHra)
            .add(earnedBonus)
            .add(earnedWashing)
            .add(earnedSpecial)
            .add(otAmount)
            .add(nhAmount)
            .add(otherEarnings);
```

---

# PF Calculation

## Formula

```text
PF Wage
=
MIN(Earned PF Wage, 15000)

PF
=
PF Wage × 12%
```

## Java

```java
BigDecimal pfApplicableWage =
        earnedBasic.min(BigDecimal.valueOf(15000));

BigDecimal pf =
        pfApplicableWage
            .multiply(BigDecimal.valueOf(0.12));
```

---

# ESIC Calculation

## Formula

```text
ESIC
=
Gross Salary × 0.75%
```

## Java

```java
BigDecimal esic =
        grossSalary
            .multiply(BigDecimal.valueOf(0.0075));
```

---

# Professional Tax

PT should be configurable per state.

```java
public interface ProfessionalTaxService {

    BigDecimal calculate(BigDecimal grossSalary);
}
```

---

# Additional Deductions

Supported deductions:

```text
PF
ESIC
Professional Tax
Labour Welfare Fund
Advance Recovery
Other Deductions
```

---

# Net Salary Formula

```text
Net Salary
=
Gross Salary
- PF
- ESIC
- Professional Tax
- Labour Welfare Fund
- Advance Recovery
- Other Deductions
```

## Java

```java
BigDecimal netSalary =
        grossSalary
            .subtract(pf)
            .subtract(esic)
            .subtract(professionalTax)
            .subtract(lwf)
            .subtract(advanceRecovery)
            .subtract(otherDeductions);
```

---

# Payroll Service Contract

```java
public interface PayrollService {

    PayrollResult calculatePayroll(
            Employee employee,
            AttendanceSummary attendance);
}
```

---

# Reference Implementation

```java
public PayrollResult calculatePayroll(
        Employee employee,
        AttendanceSummary attendance) {

    BigDecimal earnedBasic =
            prorate(employee.getBasicSalary(),
                     attendance.getWorkedDays());

    BigDecimal earnedHra =
            prorate(employee.getHra(),
                     attendance.getWorkedDays());

    BigDecimal earnedBonus =
            prorate(employee.getInterimBonus(),
                     attendance.getWorkedDays());

    BigDecimal earnedWashing =
            prorate(employee.getWashingAllowance(),
                     attendance.getWorkedDays());

    BigDecimal earnedSpecial =
            prorate(employee.getSpecialAllowance(),
                     attendance.getWorkedDays());

    BigDecimal otAmount =
            calculateOtAmount(
                    employee.getBasicSalary(),
                    attendance.getOvertimeHours());

    BigDecimal grossSalary =
            earnedBasic
                .add(earnedHra)
                .add(earnedBonus)
                .add(earnedWashing)
                .add(earnedSpecial)
                .add(otAmount);

    BigDecimal pf = calculatePf(earnedBasic);
    BigDecimal esic = calculateEsic(grossSalary);
    BigDecimal pt = professionalTaxService.calculate(grossSalary);

    BigDecimal netSalary = grossSalary
            .subtract(pf)
            .subtract(esic)
            .subtract(pt);

    return new PayrollResult();
}
```

---

# Assumptions

- Payroll divisor = 26 days.
- Standard shift duration = 8 hours.
- Overtime paid at double wages.
- Overtime calculated using Basic Salary.
- PF ceiling wage = ₹15,000.
- Employee ESIC contribution = 0.75%.
- Weekly Off worked contributes additional OT hours.
- National Holiday is treated as a paid attendance day.

---

# Future Enhancements

- Attendance rule engine.
- Shift management.
- Half-day support.
- Holiday calendar service.
- Payroll audit trail.
- REST APIs.
- Approval workflow.
- Payroll reconciliation reports.
- Multi-state statutory configuration.
