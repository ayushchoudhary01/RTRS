package com.rtrs.settlementservice.batch;

import com.rtrs.settlementservice.clearing.NettedObligation;
import com.rtrs.settlementservice.clearing.NettedObligationRepository;
import com.rtrs.settlementservice.run.SettlementRunRepository;
import com.rtrs.settlementservice.settlement.SettlementLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class EodSettlementJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final SettlementLifecycleService settlementLifecycleService;
    private final SettlementItemProcessor settlementItemProcessor;
    private final NettedObligationRepository obligationRepository;
    private final SettlementRunRepository settlementRunRepository;

    @Bean
    @JobScope
    public Step settlementStep(@Value("#{jobParameters['settlementRunId']}") String settlementRunId,
                               @Value("#{jobParameters['obligationIds']}") String obligationIdsCsv) {

        UUID runId = UUID.fromString(settlementRunId);
        List<UUID> obligationIds = Arrays.stream(obligationIdsCsv.split(","))
                .map(UUID::fromString)
                .toList();

        ListItemReader<NettedObligation> reader = new ListItemReader<>(
                obligationRepository.findAllById(obligationIds));

        SettlementItemWriter writer = new SettlementItemWriter(settlementRunRepository, runId);

        return new StepBuilder("settlementStep", jobRepository)
                .<NettedObligation, SettlementOutcome>chunk(10)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(settlementItemProcessor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(50)
                .skip(Exception.class)
                .build();
    }

    @Bean
    public Job eodSettlementJobBean(Step settlementStep) {
        return new JobBuilder("eodSettlementJob", jobRepository)
                .start(settlementStep)
                .build();
    }
}
