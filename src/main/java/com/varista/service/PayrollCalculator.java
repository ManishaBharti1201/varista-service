package com.varista.service;

import com.varista.entity.Employee;
import com.varista.model.MusterWageInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class PayrollCalculator {

    private static final BigDecimal MONTH_DIVISOR = BigDecimal.valueOf(26);
    private static final BigDecimal SHIFT_HOURS = BigDecimal.valueOf(8);
    private static final BigDecimal OVERTIME_MULTIPLIER = BigDecimal.valueOf(2);
    private static final BigDecimal HRA_RATE = new BigDecimal("0.05");
    private static final BigDecimal INTERIM_BONUS_RATE = new BigDecimal("0.0833");
    private static final BigDecimal PF_RATE = new BigDecimal("0.12");
    private static final BigDecimal ESIC_RATE = new BigDecimal("0.0075");
    private static final BigDecimal EMPLOYER_ESIC_RATE = new BigDecimal("0.0325");
    private static final BigDecimal PF_WAGE_CEILING = new BigDecimal("15000");
    private static final int CURRENCY_SCALE = 2;

    private final ProfessionalTaxService professionalTaxService;
    private final ConfiguredMinimumWageSchedule minimumWageSchedule;

    public PayrollCalculator(
            ProfessionalTaxService professionalTaxService,
            ConfiguredMinimumWageSchedule minimumWageSchedule) {
        this.professionalTaxService = professionalTaxService;
        this.minimumWageSchedule = minimumWageSchedule;
    }

    public void calculate(
            Employee employee,
            MusterWageInfo payroll,
            YearMonth payrollMonth,
            LocalDate paymentDate) {
        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date is required for payroll calculation.");
        }
        BigDecimal monthlyBasic = minimumWageSchedule.minimumWageFor(employee.getCategory(), payrollMonth);
        BigDecimal fixedHra = monthlyBasic.multiply(HRA_RATE).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal fixedBonus = monthlyBasic.multiply(INTERIM_BONUS_RATE).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal fixedWashing = money(employee.getWashingAllowance());
        BigDecimal fixedSpecial = money(employee.getSpecialAllowance());
        BigDecimal workedDays = BigDecimal.valueOf(payroll.getTotalDaysWorked());
        BigDecimal overtimeHours = BigDecimal.valueOf(payroll.getOverTimeHours());

        BigDecimal earnedBasic = prorate(monthlyBasic, workedDays);
        BigDecimal earnedHra = prorate(fixedHra, workedDays);
        BigDecimal earnedBonus = prorate(fixedBonus, workedDays);
        BigDecimal earnedWashing = prorate(fixedWashing, workedDays);
        BigDecimal earnedSpecial = prorate(fixedSpecial, workedDays);
        BigDecimal earnedOther = prorate(money(employee.getOtherAllowance()), workedDays);
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
                .add(earnedOther)
                .add(overtimeAmount)
                .add(holidayAmount)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        BigDecimal pfWage = earnedBasic.add(earnedSpecial).add(earnedWashing);
        BigDecimal pf = pfWage.min(PF_WAGE_CEILING)
                .multiply(PF_RATE)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        boolean esicCovered = employee.getEsic() != null && employee.getEsic() > 0;
        BigDecimal esicWage = gross.subtract(earnedWashing).max(BigDecimal.ZERO);
        BigDecimal esic = (esicCovered ? esicWage.multiply(ESIC_RATE) : BigDecimal.ZERO)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal employerPf = pfWage.min(PF_WAGE_CEILING)
                .multiply(PF_RATE)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal employerEsic = (esicCovered ? esicWage.multiply(EMPLOYER_ESIC_RATE) : BigDecimal.ZERO)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pt = money(professionalTaxService.calculate(gross));
        BigDecimal lwf = money(employee.getLwfDeduction());
        BigDecimal employerLwf = money(employee.getEmployerLwfContribution());
        BigDecimal advance = money(employee.getAdvanceRecovery());
        BigDecimal otherDeductions = money(employee.getOtherDeductions());
        BigDecimal totalDeductions = pf.add(esic).add(pt).add(lwf).add(advance).add(otherDeductions)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        payroll.setFixedMinimumWages(monthlyBasic.doubleValue());
        payroll.setFixedHra(fixedHra.doubleValue());
        payroll.setFixedInterimBonus(fixedBonus.doubleValue());
        payroll.setFixedWashingAllowance(fixedWashing.doubleValue());
        payroll.setFixedSpecialAllowance(fixedSpecial.doubleValue());
        payroll.setOtherAllowance(earnedOther.doubleValue());
        payroll.setEmployerLwfContribution(employerLwf.doubleValue());
        payroll.setEmployerPfContribution(employerPf.doubleValue());
        payroll.setEmployerEsicContribution(employerEsic.doubleValue());
        payroll.setDailyWage(money(monthlyBasic.divide(MONTH_DIVISOR, 8, RoundingMode.HALF_UP)).doubleValue());
        payroll.setBasicWages(earnedBasic.doubleValue());
        payroll.setHra(earnedHra.doubleValue());
        payroll.setOtAmount(overtimeAmount.doubleValue());
        payroll.setNhAmount(holidayAmount.doubleValue());
        payroll.setInterimBonus(earnedBonus.doubleValue());
        payroll.setWashingAllowance(earnedWashing.doubleValue());
        payroll.setSpecialAllowance(earnedSpecial.doubleValue());
        payroll.setDateOfPayment(paymentDate);
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
