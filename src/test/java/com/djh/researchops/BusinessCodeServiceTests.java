package com.djh.researchops;

import com.djh.researchops.mapper.BusinessCodeSequenceMapper;
import com.djh.researchops.service.BusinessCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 真实 Spring 事务代理 + Mock Mapper/事务管理器；不连接数据库，不模拟证明 InnoDB 并发行为。
class BusinessCodeServiceTests {

    private BusinessCodeSequenceMapper mapper;
    private PlatformTransactionManager transactions;
    private BusinessCodeService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessCodeSequenceMapper.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = proxy(mapper, transactions);
    }

    @ParameterizedTest
    @CsvSource({"PROJECT,0,P-1", "PROJECT,1,P-2", "TASK,0,T-1", "TASK,1,T-2", "RUN,0,R-1", "RUN,1,R-2", "TASK,6,T-7"})
    void formatsCommittedSequenceWithoutLeadingZeros(String type, long current, String expected) {
        when(mapper.lockCurrentValue(type)).thenReturn(current);
        when(mapper.advance(type, current, current + 1)).thenReturn(1);
        assertEquals(expected, next(type));
        var order = inOrder(transactions, mapper);
        order.verify(transactions).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(mapper).lockCurrentValue(type);
        order.verify(mapper).advance(type, current, current + 1);
        order.verify(transactions).commit(any());
        verifyNoMoreInteractions(mapper, transactions);
    }

    @Test
    void threeSequenceTypesRemainIndependentAcrossCallsAndServiceInstances() {
        Map<String, Long> values = new HashMap<>(Map.of("PROJECT", 0L, "TASK", 0L, "RUN", 0L));
        when(mapper.lockCurrentValue(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        when(mapper.advance(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return 1;
        });
        assertEquals("P-1", service.nextProjectCode());
        assertEquals("T-1", service.nextTaskCode());
        assertEquals("R-1", service.nextRunCode());
        assertEquals("P-2", proxy(mapper, transactions).nextProjectCode());
        assertEquals("T-2", service.nextTaskCode());
        assertEquals("R-2", service.nextRunCode());
    }

    @Test
    void missingSequenceFailsWithoutInitializingOrReturningACode() {
        when(mapper.lockCurrentValue("TASK")).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.nextTaskCode());
        verify(mapper).lockCurrentValue("TASK");
        verifyNoMoreInteractions(mapper);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    void negativeSequenceFailsWithoutAdvancing() {
        when(mapper.lockCurrentValue("RUN")).thenReturn(-1L);
        assertThrows(IllegalStateException.class, () -> service.nextRunCode());
        verify(mapper, never()).advance(anyString(), anyLong(), anyLong());
        verify(transactions).rollback(any());
    }

    @Test
    void exhaustedSequenceCannotWrapAndReuseNumbers() {
        when(mapper.lockCurrentValue("TASK")).thenReturn(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> service.nextTaskCode());
        verify(mapper, never()).advance(anyString(), anyLong(), anyLong());
        verify(transactions).rollback(any());
    }

    @Test
    void failedAdvanceRollsBackAllocationInsteadOfReturningACode() {
        when(mapper.lockCurrentValue("PROJECT")).thenReturn(8L);
        when(mapper.advance("PROJECT", 8L, 9L)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.nextProjectCode());
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
    }

    @Test
    void mapperFailureRollsBackAndPropagates() {
        RuntimeException error = new IllegalStateException("test database failure");
        when(mapper.lockCurrentValue("PROJECT")).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, () -> service.nextProjectCode()));
        verify(transactions).rollback(any());
    }

    @Test
    void allocatedCodeCommitsBeforeOuterCreateTransactionRollsBack() {
        when(mapper.lockCurrentValue("TASK")).thenReturn(8L);
        when(mapper.advance("TASK", 8L, 9L)).thenReturn(1);
        SimpleTransactionStatus outer = new SimpleTransactionStatus();
        SimpleTransactionStatus allocation = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(outer, allocation);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            assertEquals("T-9", service.nextTaskCode());
            throw new IllegalStateException("create failed after allocation");
        }));
        var order = inOrder(transactions);
        order.verify(transactions).getTransaction(argThat(definition -> definition.getPropagationBehavior() == 0));
        order.verify(transactions).getTransaction(argThat(definition -> definition.getPropagationBehavior() == 3));
        order.verify(transactions).commit(allocation);
        order.verify(transactions).rollback(outer);
    }

    @Test
    void mapperLocksPersistentSequenceAndNeverQueriesObjectCountOrIds() throws Exception {
        String read = String.join(" ", BusinessCodeSequenceMapper.class.getMethod("lockCurrentValue", String.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        String write = String.join(" ", BusinessCodeSequenceMapper.class.getMethod("advance", String.class, long.class, long.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class).value());
        assertTrue(read.contains("FOR UPDATE"));
        assertTrue(read.contains("business_code_sequence"));
        assertTrue(write.contains("business_code_sequence"));
        for (String forbidden : new String[]{"COUNT", "MAX(", "research_project", "experiment_task", "experiment_run"}) {
            assertFalse((read + write).contains(forbidden));
        }
    }

    private String next(String type) {
        return switch (type) {
            case "PROJECT" -> service.nextProjectCode();
            case "TASK" -> service.nextTaskCode();
            case "RUN" -> service.nextRunCode();
            default -> throw new IllegalArgumentException(type);
        };
    }

    private BusinessCodeService proxy(BusinessCodeSequenceMapper mapper, PlatformTransactionManager transactions) {
        ProxyFactory factory = new ProxyFactory(new BusinessCodeService(mapper));
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (BusinessCodeService) factory.getProxy();
    }
}
