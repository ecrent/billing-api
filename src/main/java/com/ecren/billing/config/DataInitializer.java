package com.ecren.billing.config;

import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.repository.PlanRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
public class DataInitializer implements ApplicationRunner {

    private final PlanRepository planRepository;

    public DataInitializer(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            return;
        }

        Plan basic = plan("Basic", "basic", 900L,
                limit(UsageMetric.API_CALLS, 10_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 5L, 50L));

        Plan pro = plan("Pro", "pro", 2900L,
                limit(UsageMetric.API_CALLS, 100_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 50L, 30L));

        Plan enterprise = plan("Enterprise", "enterprise", 9900L,
                limit(UsageMetric.API_CALLS, 1_000_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 500L, 20L));

        planRepository.saveAll(List.of(basic, pro, enterprise));
    }

    private Plan plan(String name, String slug, long basePriceCents, PlanMetricLimit... limits) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setBasePriceCents(basePriceCents);
        for (PlanMetricLimit limit : limits) {
            limit.setPlan(plan);
        }
        plan.setMetricLimits(List.of(limits));
        return plan;
    }

    private PlanMetricLimit limit(UsageMetric metric, long included, long overageCents) {
        PlanMetricLimit l = new PlanMetricLimit();
        l.setMetric(metric);
        l.setIncludedQuantity(included);
        l.setOveragePricePerUnitCents(overageCents);
        return l;
    }
}
