package com.github.houbb.junitperf.support.statements;

import com.github.houbb.junitperf.core.report.Reporter;
import com.github.houbb.junitperf.core.statistics.StatisticsCalculator;
import com.github.houbb.junitperf.model.evaluation.EvaluationContext;
import com.github.houbb.junitperf.support.task.PerformanceEvaluationTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.runners.model.Statement;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试 statement
 * @author bbhou
 * @version 1.0.0
 * @since 1.0.0, 2018/01/11
 * @see com.github.houbb.junitperf.core.rule.JunitPerfRule 用于此规则
 */
public class PerformanceEvaluationStatement extends Statement {

    private static final String THREAD_NAME_PATTERN = "performance-evaluation-thread-%d";
    private static final ThreadFactory FACTORY = new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_PATTERN).build();

    private final EvaluationContext evaluationContext;
    private final Statement statement;
    private final StatisticsCalculator statisticsCalculator;
    private final Set<Reporter> reporterSet;
    private final Set<EvaluationContext> evaluationContextSet;
    private final Class testClass;

    /**
     * 性能测试接口定义
     * @param evaluationContext 上下文
     * @param statement junit
     * @param statisticsCalculator 统计
     * @param reporterSet 报告方式
     * @param evaluationContextSet 上下文
     * @param testClass 当前测试 class 信息
     */
    public PerformanceEvaluationStatement(EvaluationContext evaluationContext,
                                          Statement statement,
                                          StatisticsCalculator statisticsCalculator,
                                          Set<Reporter> reporterSet,
                                          Set<EvaluationContext> evaluationContextSet,
                                          final Class testClass) {
        this.evaluationContext = evaluationContext;
        this.statement = statement;
        this.statisticsCalculator = statisticsCalculator;

        //报告生成
        this.reporterSet = reporterSet;
        this.evaluationContextSet = evaluationContextSet;
        this.testClass = testClass;
    }

    @Override
    public void evaluate() throws Throwable {
        List<Thread> threadList = new LinkedList<>();

        try {
            for(int i = 0; i < evaluationContext.getConfigThreads(); i++) {
                PerformanceEvaluationTask task = new PerformanceEvaluationTask(evaluationContext.getConfigWarmUp(),
                        statement, statisticsCalculator);
                Thread t = FACTORY.newThread(task);
                threadList.add(t);
                t.start();
            }

            Thread.sleep(evaluationContext.getConfigDuration());
        } finally {
            //具体详情，当执行打断时，被打断的任务可能已经开始执行(尚未执行完)，会出现主线程往下走，被打断的线程也在继续走的情况
            for(Thread thread : threadList) {
                thread.interrupt();
            }
        }


        evaluationContext.setStatisticsCalculator(statisticsCalculator);
        evaluationContext.runValidation();

        //是否根据最大的时间进行沉睡，保证执行完成？
        //TODO: 经测试这并不是很有效的方式
        long max = (long) statisticsCalculator.getMaxLatency(TimeUnit.MILLISECONDS);
        Thread.sleep(max);

        generateReportor();
    }

    /**
     * 报告生成
     */
    private synchronized void generateReportor() {
        for(Reporter reporter : reporterSet) {
            reporter.report(testClass, evaluationContextSet);
        }
    }

}
