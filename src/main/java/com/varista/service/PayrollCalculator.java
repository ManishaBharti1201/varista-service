package com.varista.service;

import com.varista.entity.Employee;
import com.varista.model.MusterWageInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PayrollCalculator {

    private static final BigDecimal MONTH_DIVISOR = BigDecimal.valueOf(26);
    private static final BigDecimal SHIFT_HOURS = BigDecimal.valueOf(8);
    private static final BigDecimal OVERTIME_MULTIPLIER = BigDecimal.valueOf(2);
    private static final BigDecimal PF_RATE = new BigDecimal("0.12");
    private static final BigDecimal ESIC_RATE = new BigDecimal("0.0075");
    private static final BigDecimal PF_WAGE_CEILING = new BigDecimal("15000");
    private static final int CURRENCY_SCALE = 2;

    private final ProfessionalTaxService professionalTaxService;

    public PayrollCalculator(ProfessionalTaxService professionalTaxService) {
        this.professionalTaxService = professionalTaxService;
    }

    public void calculate(Employee employee, MusterWageInfo payroll) {
        BigDecimal monthlyBasic = valueOrZero(employee.getBasicSalary());
        BigDecimal workedDays = BigDecimal.valueOf(payroll.getTotalDaysWorked());
        BigDecimal overtimeHours = BigDecimal.valueOf(payroll.getOverTimeHours());

        BigDecimal earnedBasic = prorate(monthlyBasic, workedDays);
        BigDecimal earnedHra = prorate(valueOrZero(employee.getHra()), workedDays);
        BigDecimal earnedBonus = prorate(valueOrZero(employee.getInterimBonus()), workedDays);
        BigDecimal earnedWashing = prorate(valueOrZero(employee.getWashingAllowance()), workedDays);
        BigDecimal earnedSpecial = prorate(valueOrZero(employee.getSpecialAllowance()), workedDays);
        BigDecimal overtimeAmount = monthlyBasic
                .divide(MONTH_DIVISOR, 8, RoundingMode.HALF_UP)
                .divide(SHIFT_HOURS, 8, RoundingMode.HALF_UP)
                .multiply(OVERTIME_MULTIPLIER)
                .multiply(overtimeHours)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        // Holiday days are included in paid days; the specification defines no separate NH premium.
        BigDecimal holidayAmount = BigDecimal.ZERO;
        BigDecimal gross = earnedBasic
                .add(earnedHra)
                .add(earnedBonus)
                .add(earnedWashing)
                .add(earnedSpecial)
                .add(overtimeAmount)
                .add(holidayAmount)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        BigDecimal pf = earnedBasic.min(PF_WAGE_CEILING)
                .multiply(PF_RATE)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal esic = gross.multiply(ESIC_RATE)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pt = money(professionalTaxService.calculate(gross));
        BigDecimal lwf = money(employee.getLwfDeduction());
        BigDecimal advance = money(employee.getAdvanceRecovery());
        BigDecimal otherDeductions = money(employee.getOtherDeductions());
        BigDecimal totalDeductions = pf.add(esic).add(pt).add(lwf).add(advance).add(otherDeductions)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        payroll.setDailyWage(money(monthlyBasic.divide(MONTH_DIVISOR, 8, RoundingMode.HALF_UP)).doubleValue());
        payroll.setBasicWages(earnedBasic.doubleValue());
        payroll.setHra(earnedHra.doubleValue());
        payroll.setOtAmount(overtimeAmount.doubleValue());
        payroll.setNhAmount(holidayAmount.doubleValue());
        payroll.setInterimBonus(earnedBonus.doubleValue());
        payroll.setWashingAllowance(earnedWashing.doubleValue());
        payroll.setSpecialAllowance(earnedSpecial.doubleValue());
        payroll.setGrossWage(gross.doubleValue());
        payroll.setPfDeduction(pf.doubleValue());
        payroll.setEsicDeduction(esic.doubleValue());
        payroll.setPtDeduction(pt.doubleValue());
        payroll.setLwfDeduction(lwf.doubleValue());
        payroll.setAdvanceRecovery(advance.doubleValue());
        payroll.setOtherDeductions(otherDeductions.doubleValue());
        payroll.setTotalDeductions(totalDeductions.doubleValue());
        payroll.setNetWage(gross.subtract(totalDeductions).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP).doubleValue());
    }

    private static BigDecimal prorate(BigDecimal monthlyAmount, BigDecimal workedDays) {
        return monthlyAmount.multiply(workedDays)
                .divide(MONTH_DIVISOR, CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return valueOrZero(amount).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal valueOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
